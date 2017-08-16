import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.io.StringReader;
import java.util.Iterator;
import java.util.List;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;


import java.util.Collection;  
import edu.stanford.nlp.trees.TreePrint;  
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;  
import edu.stanford.nlp.trees.Tree;  
import edu.stanford.nlp.trees.TypedDependency;  
import edu.stanford.nlp.trees.international.pennchinese.ChineseGrammaticalStructure;

public class QAParser{

    public static LexicalizedParser lp = null;

    public static void main(String[] args) throws Exception {
	String modelpath="edu/stanford/nlp/models/lexparser/xinhuaFactoredSegmenting.ser.gz";
	System.out.println("Loading model...");
        lp = LexicalizedParser.loadModel(modelpath);
        if (lp == null) {
		System.out.println("Failed to load model");
		return ;
	}

	System.out.println("Starting server...");
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/parse", new MyHandler());
        server.start();
    }

    static class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
	    String sent = (String)t.getRequestURI().getQuery();
            System.out.println("Parsing: " + sent);
            String response = Parse(sent);
	    System.out.println("Parse done");
	    System.out.println(response);
try{
	    t.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            t.sendResponseHeaders(200, response.getBytes("UTF8").length);
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes("UTF8"));
            os.close();
}catch(IOException e){
	System.out.println(e);
}
        }
    }

   static String Parse(String sent){
        System.out.println("Parse begin...");
	Tree t = lp.parse(sent);
	//t.pennPrint();
	
	ChineseGrammaticalStructure gs = new ChineseGrammaticalStructure(t);  
        Collection<TypedDependency> tdl = gs.typedDependenciesCollapsed();  
	//System.out.println(tdl);
	for(int i = 0;i < tdl.size();i ++)  
	{  
		//TypedDependency(GrammaticalRelation reln, TreeGraphNode gov, TreeGraphNode dep)  
		TypedDependency td = (TypedDependency)tdl.toArray()[i];  
		//System.out.println(td.toString());  
	} 

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
	//System.out.print(pennTree);
	return pennTree; 
   }
}




     