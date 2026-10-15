import javax.tools.*;
import com.sun.source.util.JavacTask;
import java.util.*;
public class ParseSources {
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null)) {
            JavacTask task = (JavacTask)compiler.getTask(null, files, diagnostics, Arrays.asList("-proc:none", "-source", "8"), null, files.getJavaFileObjects(args));
            task.parse();
            for (Diagnostic<?> d : diagnostics.getDiagnostics()) {
                if (d.getKind() == Diagnostic.Kind.ERROR) throw new AssertionError(d.toString());
            }
        }
        System.out.println("PASS: Java 8 syntax for " + args.length + " sources (not Android type checking)");
    }
}
