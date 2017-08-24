import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StreamCorruptedException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.parser.lexparser.BinaryGrammar;
import edu.stanford.nlp.parser.lexparser.DependencyGrammar;
import edu.stanford.nlp.parser.lexparser.EvaluateTreebank;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.Lexicon;
import edu.stanford.nlp.parser.lexparser.MLEDependencyGrammar;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.parser.lexparser.UnaryGrammar;
import edu.stanford.nlp.parser.lexparser.UnknownWordModel;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.process.DocumentPreprocessor.DocType;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.ReflectionLoading;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.util.logging.Redwood;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * 
 * @author Administrator
 * @Usage: 1) parse with xinhua; 2) parse with user corpus; 3) train with new
 *         corpus: note, you must be a linguist, when facing a strange sentence,
 *         try to parse it with any tools(xinhua parse and replace, or
 *         manually), and pass the tagged-sent to train.
 */

public class QAParser {

	/** A logger for this class */
	private static final Redwood.RedwoodChannels log = Redwood.channels(LexicalizedParser.class);

	public static LexicalizedParser lp = null;
	public static LexicalizedParser pcfgparser = null;
	public static LexicalizedParser scorer = null;
	public static List<LexicalizedParser> lps = new ArrayList<LexicalizedParser>();
	public static CRFClassifier<CoreLabel> segmenter = null;

	public static void main(String[] args) throws Exception {
		// 1) XinhuaFacSeg parser
		String modelpath = "edu/stanford/nlp/models/lexparser/xinhuaFactoredSegmenting.ser.gz";
		System.out.println("Loading default model...");
		lp = LexicalizedParser.loadModel(modelpath);
		if (lp == null) {
			System.out.println("Failed to load defualt model");
			return;
		}

		// 2) PCFG parser
		String pcfgmodel = "edu/stanford/nlp/models/lexparser/chinesePCFG.ser.gz";
		System.out.println("Loading PCFG model...");
		pcfgparser = LexicalizedParser.loadModel(pcfgmodel);
		if (pcfgparser == null) {
			System.out.println("Failed to load PCFG model");
			return;
		}

		// 3) self-trained parsers
		for (int i = 0; i < 0x400; i++) {
			String modelfile = String.format("trained-%d.ser.gz", i);
			File f = new File(modelfile);
			if (!f.exists() || f.isDirectory()) {
				break;
			}
			System.out.println("Loading custom trained model: " + modelfile);
			lps.add(LexicalizedParser.loadModel(modelfile));
		}

		// 4) Loading segmenter
		if (initSegmenter()) {
			System.out.println("Loaded Segmenter");
		} else {
			System.out.println("Failed to load Segmenter");
		}

		// 5) Starting daemon
		short port = 8000;
		System.out.println("Starting server..., port: " + port);
		HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

		// Parse a sentence and return tagged array of words.
		// a) default parser, the xinhuaFacttoredSegmenting;
		// b) then the custom models, trained-NNN.ser.gz
		// Note: input must be segmented, before parsed by custom trained
		// data.
		System.out.println("Creating parser handler");
		server.createContext("/parse", new ContextHandler());

		// Train with manually-tagged sentence from linguist;
		System.out.println("Creating train handler");
		server.createContext("/train", new ContextHandler());

		// Create a handler for xinhua factored segmented parser
		System.out.println("Creating xinhua factored segmented parser handler");
		server.createContext("/xinhuaparse", new ContextHandler());

		// Score a group of sentences on trained data, and give a sort for
		// suggestion;
		System.out.println("Creating suggest handler");
		server.createContext("/suggest", new ContextHandler());

		// Score a group of sentences on trained data, and give a sort for
		// suggestion;
		System.out.println("Generating sentence handler");
		server.createContext("/gen", new ContextHandler());

		// Create a handler for PCFG parser
		System.out.println("Creating PCFG parser handler");
		server.createContext("/pcfgparse", new ContextHandler());

		// Segment a sentence on user dictionary;
		System.out.println("Creating segment handler");
		server.createContext("/segment", new ContextHandler());

		server.start();
	}

	static class ContextHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange t) throws IOException {
			String ctx = t.getRequestURI().getPath();
			System.out.println("context: " + ctx);
			String qry = (String) t.getRequestURI().getQuery();
			String resp = "";
			if (ctx.equals("/parse")) {
				resp = SegParse(qry);
			} else if (ctx.equals("/xinhuaparse")) {
				resp = XinHuaFacSegParse(qry);
			} else if (ctx.equals("/segment")) {
				resp = Segment(qry).toString();
			} else if (ctx.equals("/suggest")) {
				resp = "" + Suggest(qry);
			} else if (ctx.equals("/train")) {
				resp = Train(qry);
			} else if (ctx.equals("/gen")) {
				resp = Gen(qry);
			} else {

			}
			System.out.println(resp);
			try {
				t.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
				t.sendResponseHeaders(200, resp.getBytes("UTF8").length);
				OutputStream os = t.getResponseBody();
				os.write(resp.getBytes("UTF8"));
				os.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static String SegParse(String sent) {
		List<String> segged = Segment(sent);

		JSONObject r = new JSONObject();
		int i = 0;
		for (LexicalizedParser parser : lps) {
			Tree t = parser.parseStrings(segged);
			JSONArray a = toWords(t);
			r.put("" + (i++), a);
		}

		return r.toString();
	}

	public static String XinHuaFacSegParse(String sent) {
		return toWords(lp.parse(sent)).toString();
	}

	public static String Train(String taggedSent) {
		// pcfgparser.setOptionFlags("-tokenized", "-encoding", "utf-8",
		// "-sentences", "newline", "-tagSeparator", "/",
		// "-tokenizerFactory", "edu.stanford.nlp.process.WhitespaceTokenizer",
		// "-tokenizerMethod",
		// "newCoreLabelTokenizerFactory", "-outputFormat",
		// "penn,typedDependenciesCollapsed");

		try {
			Class<TokenizerFactory<? extends HasWord>> clazz = ErasureUtils
					.uncheckedCast(Class.forName("edu.stanford.nlp.process.WhitespaceTokenizer"));

			Method factoryMethod = clazz.getMethod("newCoreLabelTokenizerFactory");

			TokenizerFactory<? extends HasWord> tokenizerFactory = ErasureUtils
					.uncheckedCast(factoryMethod.invoke(null));

			InputStream is = new ByteArrayInputStream(taggedSent.getBytes());

			BufferedReader reader = new BufferedReader(new InputStreamReader(is));

			final DocumentPreprocessor documentPreprocessor = new DocumentPreprocessor(reader, DocType.Plain);

			// Unused values are null per the main() method invocation below
			// null is the default for these properties
			// documentPreprocessor.setSentenceFinalPuncWords(tlp.sentenceFinalPunctuationWords());
			documentPreprocessor.setSentenceDelimiter("\n");
			documentPreprocessor.setTagDelimiter("/");
			documentPreprocessor.setTokenizerFactory(tokenizerFactory);

			Tree t = null;

			for (List<HasWord> sentence : documentPreprocessor) {
				t = lp.parse(sentence);

				// Append new corpus
				Append("./corpus.txt", t.pennString());

				// Append new vocabulary
				JSONArray words = toWords(t);
				for (int i = 0; i < words.size(); i++) {
					JSONObject o = words.optJSONObject(i);
					String word = o.optString("word");
					Append("./dict.txt", word);
				}
				break;
			}

			return t.pennString();

		} catch (Exception e) {
			e.printStackTrace();
		}

		return "";
	}

	private static JSONArray toWords(Tree t) {
		// t.pennPrint();

		// ChineseGrammaticalStructure gs = new ChineseGrammaticalStructure(t);
		// Collection<TypedDependency> tdl = gs.typedDependenciesCollapsed();
		// System.out.println(tdl);-
		// for (int i = 0; i < tdl.size(); i++) {
		// TypedDependency(GrammaticalRelation reln, TreeGraphNode gov,
		// TreeGraphNode dep)
		// TypedDependency td = (TypedDependency) tdl.toArray()[i];
		// System.out.println(td.toString());
		// }

		List<Tree> leaves = t.getLeaves();
		Iterator<Tree> it = leaves.iterator();
		JSONArray a = new JSONArray();
		while (it.hasNext()) {
			Tree leaf = it.next();
			Tree start = leaf.parent(t);
			String tag = start.value().toString().trim();
			// System.out.println(tag);
			// System.out.println(leaf.nodeString().trim());
			JSONObject o = new JSONObject();
			o.put("tag", tag);
			o.put("word", leaf.nodeString().trim());
			a.add(o);
		}
		return a;

		// String pennTree = t.pennString();
		// return pennTree;
	}

	private static boolean initSegmenter() {
		String basedir = "./segmenter/data";

		Properties props = new Properties();
		props.setProperty("sighanCorporaDict", basedir);
		// props.setProperty("NormalizationTable", "data/norm.simp.utf8");
		// props.setProperty("normTableEncoding", "UTF-8");
		// below is needed because CTBSegDocumentIteratorFactory accesses it

		System.out.println("Loading user dictionary...");
		props.setProperty("serDictionary", basedir + "/dict-chris6.ser.gz," + "./dict.txt");

		/*
		 * if (args.length > 0) { props.setProperty("testFile", args[0]); }
		 */
		props.setProperty("inputEncoding", "UTF-8");
		props.setProperty("sighanPostProcessing", "true");
		props.setProperty("keepAllWhitespaces", "false");

		if (segmenter == null) {
			System.out.println("Creating new Segmenter...");
			segmenter = new CRFClassifier<>(props);
		}

		System.out.println("Loading classifier...");
		segmenter.loadClassifierNoExceptions(basedir + "/ctb.gz", props);

		/*
		 * for (String filename : args) {
		 * segmenter.classifyAndWriteAnswers(filename); }
		 */

		return true;
	}

	public static List<String> Segment(String sent) {

		List<String> segmented = segmenter.segmentString(sent);
		// System.out.println(segmented);
		return segmented;
	}

	static double Suggest(String sentence) {
		double score = Double.NEGATIVE_INFINITY;
		for (LexicalizedParser parser : lps) {
			Tree t = parser.parseStrings(Segment(sentence));
			/*
			 * Append("./tmp.txt", t.pennString());
			 * 
			 * BinaryGrammar bg = parser.bg; UnaryGrammar ug = parser.ug;
			 * Lexicon lex = parser.lex;
			 * 
			 * Index<String> stateIndex = parser.stateIndex; Index<String>
			 * wordIndex = new DeltaIndex<>(parser.wordIndex); Index<String>
			 * tagIndex = parser.tagIndex;
			 * 
			 * ExhaustivePCFGParser exhparser = new ExhaustivePCFGParser(bg, ug,
			 * lex, parser.getOp(), stateIndex, wordIndex, tagIndex);
			 * 
			 * // exhparser.scoreNonBinarizedTree(t);
			 * 
			 * exhparser.scoreBinarizedTree(t, 0);
			 */
			score = t.score();

			// score = TestOnTreebank(parser, t);

		}
		System.out.println("Score: " + score);
		return score;
	}

	public static String Gen(String sentence) {
		for (LexicalizedParser parser : lps) {
			Tree t = parser.parseStrings(Segment(sentence));
			List<? extends HasWord> words = SentenceUtils.toCoreLabelList(t.yieldWords());
			System.out.println(t.pennString());
			return SentenceUtils.listToString(words);
		}
		return "";
	}

	private static boolean Append(String path, String content) {
		try {
			Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path, true), "UTF8"));
			out.append(content).append("\r\n");
			out.flush();
			out.close();
		} catch (UnsupportedEncodingException e) {
			System.out.println(e.getMessage());
			return false;
		} catch (IOException e) {
			System.out.println(e.getMessage());
			return false;
		} catch (Exception e) {
			System.out.println(e.getMessage());
			return false;
		}
		return true;
	}

	private static double TestOnTreebank(LexicalizedParser parser, Tree t) {
		double score = Double.NEGATIVE_INFINITY;

		Options op = new Options();
		op.tlpParams.setInputEncoding("utf-8");
		op.tlpParams.setOutputEncoding("utf-8");

		List<String> optionArgs = new ArrayList<>();
		optionArgs.add("-outputFormat");
		optionArgs.add("penn,typedDependenciesCollapsed");
		optionArgs.add("-v");
		String[] extraArgs = new String[optionArgs.size()];
		extraArgs = optionArgs.toArray(extraArgs);

		parser = LexicalizedParser.loadModel("./trained-0.ser.gz", op, extraArgs);
		op = parser.getOp();

		Treebank testTreebank = op.tlpParams.testMemoryTreebank();
		// testTreebank.loadPath("./tmp.txt", null);
		testTreebank.add(t);

		op.trainOptions.sisterSplitters = Generics.newHashSet(Arrays.asList(op.tlpParams.sisterSplitters()));

		EvaluateTreebank evaluator = new EvaluateTreebank(parser);
		evaluator.testOnTreebank(testTreebank);

		return score;
	}

	public static LexicalizedParser loadModel(String parserFileOrUrl, Options op, String... extraFlags) {
		// log.info("Loading parser from file " + parserFileOrUrl);
		LexicalizedParser parser = getParserFromFile(parserFileOrUrl, op);
		if (extraFlags.length > 0) {
			parser.setOptionFlags(extraFlags);
		}
		return parser;
	}

	public static LexicalizedParser getParserFromFile(String parserFileOrUrl, Options op) {
		LexicalizedParser pd = getParserFromSerializedFile(parserFileOrUrl);
		if (pd == null) {
			pd = getParserFromTextFile(parserFileOrUrl, op);
		}
		return pd;
	}

	public static LexicalizedParser getParserFromSerializedFile(String serializedFileOrUrl) {
		try {
			Timing tim = new Timing();
			ObjectInputStream in = IOUtils.readStreamFromString(serializedFileOrUrl);
			LexicalizedParser pd = loadModel(in);

			in.close();
			log.info("Loading parser from serialized file " + serializedFileOrUrl + " ... done ["
					+ tim.toSecondsString() + " sec].");
			return pd;
		} catch (InvalidClassException ice) {
			// For this, it's not a good idea to continue and try it as a text
			// file!
			throw new RuntimeException("Invalid class in file: " + serializedFileOrUrl, ice);
		} catch (FileNotFoundException fnfe) {
			// For this, it's not a good idea to continue and try it as a text
			// file!
			throw new RuntimeException("File not found: " + serializedFileOrUrl, fnfe);
		} catch (StreamCorruptedException sce) {
			// suppress error message, on the assumption that we've really got
			// a text grammar, and that'll be tried next
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static LexicalizedParser loadModel(ObjectInputStream ois) {
		try {
			Object o = ois.readObject();
			if (o instanceof LexicalizedParser) {
				return (LexicalizedParser) o;
			}
			throw new ClassCastException("Wanted LexicalizedParser, got " + o.getClass());
		} catch (IOException e) {
			throw new RuntimeIOException(e);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	protected static LexicalizedParser getParserFromTextFile(String textFileOrUrl, Options op) {
		try {
			Timing tim = new Timing();
			BufferedReader in = IOUtils.readerFromString(textFileOrUrl);
			Timing.startTime();

			String line = in.readLine();
			confirmBeginBlock(textFileOrUrl, line);
			op.readData(in);

			line = in.readLine();
			confirmBeginBlock(textFileOrUrl, line);
			Index<String> stateIndex = HashIndex.loadFromReader(in);

			line = in.readLine();
			confirmBeginBlock(textFileOrUrl, line);
			Index<String> wordIndex = HashIndex.loadFromReader(in);

			line = in.readLine();
			confirmBeginBlock(textFileOrUrl, line);
			Index<String> tagIndex = HashIndex.loadFromReader(in);

			line = in.readLine();
			confirmBeginBlock(textFileOrUrl, line);
			Lexicon lex = op.tlpParams.lex(op, wordIndex, tagIndex);
			String uwmClazz = line.split(" +")[2];
			if (!uwmClazz.equals("null")) {
				UnknownWordModel model = ReflectionLoading.loadByReflection(uwmClazz, op, lex, wordIndex, tagIndex);
				lex.setUnknownWordModel(model);
			}
			lex.readData(in);

			line = in.readLine();
			confirmBeginBlock(textFileOrUrl, line);
			UnaryGrammar ug = new UnaryGrammar(stateIndex);
			ug.readData(in);

			line = in.readLine();
			confirmBeginBlock(textFileOrUrl, line);
			BinaryGrammar bg = new BinaryGrammar(stateIndex);
			bg.readData(in);

			line = in.readLine();
			confirmBeginBlock(textFileOrUrl, line);
			DependencyGrammar dg = new MLEDependencyGrammar(op.tlpParams, op.directional, op.distance,
					op.coarseDistance, op.trainOptions.basicCategoryTagsInDependencyGrammar, op, wordIndex, tagIndex);
			dg.readData(in);

			in.close();
			log.info("Loading parser from text file " + textFileOrUrl + " ... done [" + tim.toSecondsString()
					+ " sec].");
			return new LexicalizedParser(lex, bg, ug, dg, stateIndex, wordIndex, tagIndex, op);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	private static void confirmBeginBlock(String file, String line) {
		if (line == null) {
			throw new RuntimeException(file + ": expecting BEGIN block; got end of file.");
		} else if (!line.startsWith("BEGIN")) {
			throw new RuntimeException(file + ": expecting BEGIN block; got " + line);
		}
	}

}
