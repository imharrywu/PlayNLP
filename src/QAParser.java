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

		System.out.println("Creating parser handler");
		server.createContext("/parse", new ParseHandler());

		System.out.println("Creating suggest handler");
		server.createContext("/suggest", new SuggestHandler());

		server.start();
	}

	static class ParseHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange t) throws IOException {
			String sent = (String) t.getRequestURI().getQuery();
			System.out.println("Parsing: " + sent);
			String response = Parse(sent);
			System.out.println("Parse done");
			System.out.println(response);
			try {
				t.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
				t.sendResponseHeaders(200, response.getBytes("UTF8").length);
				OutputStream os = t.getResponseBody();
				os.write(response.getBytes("UTF8"));
				os.close();
			} catch (IOException e) {
				System.out.println(e);
			}
		}
	}

	static class SuggestHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange t) throws IOException {
			String sent = (String) t.getRequestURI().getQuery();
			System.out.println("Scoring: " + sent);
			String response = Parse(sent);
			System.out.println("Score done");
			System.out.println(response);
			try {
				t.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
				t.sendResponseHeaders(200, response.getBytes("UTF8").length);
				OutputStream os = t.getResponseBody();
				os.write(response.getBytes("UTF8"));
				os.close();
			} catch (IOException e) {
				System.out.println(e);
			}
		}
	}

	static String Parse(String sent) {
		System.out.println("Parse begin...");
		String result = "";
		Tree t = lp.parse(sent);
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
		while (it.hasNext()) {
			Tree leaf = it.next();
			Tree start = leaf.parent(t);
			String tag = start.value().toString().trim();
			// System.out.println(tag);
			// System.out.println(leaf.nodeString().trim());
			JsonObject o = new JSONObject();
			result += tag;
			result += leaf.nodeString().trim();
		}

		return result;

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
