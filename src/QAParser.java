import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.trees.Tree;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class QAParser {

	public static LexicalizedParser lp = null;
	public static List<LexicalizedParser> lps = new ArrayList<LexicalizedParser>();

	public static void main(String[] args) throws Exception {
		String modelpath = "edu/stanford/nlp/models/lexparser/xinhuaFactoredSegmenting.ser.gz";
		System.out.println("Loading default model...");
		lp = LexicalizedParser.loadModel(modelpath);
		if (lp == null) {
			System.out.println("Failed to load defualt model");
			return;
		}

		for (int i = 0; i < 0x400; i++) {
			String modelfile = String.format("trained-%d.ser.gz", i);
			File f = new File(modelfile);
			if (!f.exists() || f.isDirectory()) {
				break;
			}
			System.out.println("Loading custom trained model: " + modelfile);
			lps.add(LexicalizedParser.loadModel(modelfile));
		}

		System.out.println("Starting server...");
		HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);

		// Parse a sentence and return tagged array of words.
		// 1) default parser, the xinhuaFacttoredSegmenting;
		// 2) then the custom models, trained-NNN.ser.gz
		// Note: input must be segmented, before parsed by custom trained
		// data.
		System.out.println("Creating parser handler");
		server.createContext("/parse", new ContextHandler());

		// Score a group of sentences on trained data, and give a sort for
		// suggestion;
		System.out.println("Creating suggest handler");
		server.createContext("/suggest", new ContextHandler());

		// Train with manually-tagged sentence from linguist;
		System.out.println("Creating train handler");
		server.createContext("/train", new ContextHandler());

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
				resp = Parse(qry);
			} else if (ctx.equals("/segment")) {

			} else if (ctx.equals("/suggest")) {

			} else if (ctx.equals("/train")) {

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
				System.out.println(e);
			}
		}
	}

	public static String Parse(String sent) {
		JSONObject r = new JSONObject();
		JSONArray a = Parse(lp, sent);
		r.put("default", a);
		int i = 0;
		for (LexicalizedParser parser : lps) {
			a = Parse(parser, sent);
			r.put("" + (i++), a);
		}
		return r.toString();
	}

	private static JSONArray Parse(LexicalizedParser parser, String sent) {
		System.out.println("Parse begin...");
		Tree t = parser.parse(sent);
		// t.pennPrint();

		// ChineseGrammaticalStructure gs = new ChineseGrammaticalStructure(t);
		// Collection<TypedDependency> tdl = gs.typedDependenciesCollapsed();
		// System.out.println(tdl);
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

	static String Suggest(List<String> sents) {
		System.out.println("Parse begin...");
		Tree t = lp.parse("");
		List<Tree> leaves = t.getLeaves();
		Iterator<Tree> it = leaves.iterator();
		while (it.hasNext()) {
			Tree leaf = it.next();
			Tree start = leaf.parent(t);
			String tag = start.value().toString().trim();
			System.out.println(tag);
			System.out.println(leaf.nodeString().trim());
		}
		String pennTree = t.pennString();
		return pennTree;
	}

}
