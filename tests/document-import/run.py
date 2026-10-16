"""Exercise provider streams and cleanup with JVM Android API doubles."""
import pathlib, subprocess, tempfile
root = pathlib.Path(__file__).resolve().parents[2]
stubs = {
'android/net/Uri.java': 'package android.net; public class Uri {}',
'android/provider/OpenableColumns.java': 'package android.provider; public class OpenableColumns { public static final String DISPLAY_NAME="name"; }',
'android/database/Cursor.java': 'package android.database; public interface Cursor extends AutoCloseable { boolean moveToFirst(); int getColumnIndex(String n); String getString(int i); void close(); }',
'android/content/ContentResolver.java': '''package android.content; import java.io.*; import android.net.Uri; import android.database.Cursor; public class ContentResolver {
public InputStream stream; public boolean denied;
public InputStream openInputStream(Uri u) { if(denied) throw new SecurityException(); return stream; }
public Cursor query(Uri u,String[] p,String s,String[] a,String o) { return null; }
}''',
'android/content/Context.java': '''package android.content; import java.io.File; public class Context {
public File cache; public ContentResolver resolver=new ContentResolver();
public File getCacheDir(){return cache;} public ContentResolver getContentResolver(){return resolver;}
}''',
'Test.java': '''import java.io.*; import java.nio.file.*; import java.util.*;
import android.content.Context; import android.net.Uri; import com.winlator.core.DocumentImport;
public class Test {
public static void main(String[] a) throws Exception {
 Context c=new Context(); c.cache=new File(a[0]); Uri u=new Uri();
 byte[] bytes=new byte[200000]; new Random(42).nextBytes(bytes);
 c.resolver.stream=new ByteArrayInputStream(bytes);
 File f=DocumentImport.copyToCache(c,u);
 if(!Arrays.equals(bytes,Files.readAllBytes(f.toPath()))) throw new AssertionError("copy");
 f.delete();
 c.resolver.stream=null; expectFailure(c,u);
 c.resolver.denied=true; expectFailure(c,u); c.resolver.denied=false;
 c.resolver.stream=new InputStream(){public int read() throws IOException {throw new IOException("broken provider");}};
 expectFailure(c,u); expectFailure(c,null);
 System.out.println("PASS: document bytes, missing stream, denied access, read failure, null URI and temporary cleanup");
}
static void expectFailure(Context c,Uri u) throws Exception {
 try { DocumentImport.copyToCache(c,u); throw new AssertionError("expected failure"); }
 catch(IOException|SecurityException expected){}
 if(c.cache.list().length!=0) throw new AssertionError("temporary leak");
}
}'''
}
with tempfile.TemporaryDirectory() as temp:
    p=pathlib.Path(temp)
    files=[]
    for name,content in stubs.items():
        f=p/name; f.parent.mkdir(parents=True,exist_ok=True); f.write_text(content); files.append(str(f))
    cache=p/'cache';cache.mkdir()
    sources=[root/'app/src/main/java/com/winlator/core'/n for n in ['DocumentImport.java','StreamUtils.java']]
    subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',str(p/'classes'),*files,*map(str,sources)],check=True)
    subprocess.run(['java','-cp',str(p/'classes'),'Test',str(cache)],check=True)
