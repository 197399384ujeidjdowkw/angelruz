package cl.halconimpresiones.mobile;
import android.content.*;import android.database.*;import android.database.MatrixCursor;import android.net.Uri;import android.os.ParcelFileDescriptor;import android.provider.OpenableColumns;import java.io.*;
public class ShareFileProvider extends ContentProvider{
 public boolean onCreate(){return true;} File f(Uri u)throws FileNotFoundException{String n=u.getLastPathSegment();if(n==null||n.contains("..")||n.contains("/"))throw new FileNotFoundException();File x=new File(getContext().getCacheDir(),n);if(!x.exists())throw new FileNotFoundException();return x;}
 public String getType(Uri u){String n=u.getLastPathSegment();return n!=null&&n.endsWith(".pdf")?"application/pdf":"image/png";}
 public ParcelFileDescriptor openFile(Uri u,String m)throws FileNotFoundException{return ParcelFileDescriptor.open(f(u),ParcelFileDescriptor.MODE_READ_ONLY);}
 public Cursor query(Uri u,String[] p,String s,String[] a,String o){try{File x=f(u);MatrixCursor c=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});c.addRow(new Object[]{x.getName(),x.length()});return c;}catch(Exception e){return null;}}
 public int delete(Uri u,String s,String[] a){return 0;}public int update(Uri u,ContentValues v,String s,String[] a){return 0;}public Uri insert(Uri u,ContentValues v){return null;}
}