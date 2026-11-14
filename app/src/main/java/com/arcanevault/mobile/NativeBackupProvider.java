package com.arcanevault.mobile;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;

/** Private, read-only content URIs for explicit Android collection exports. */
public final class NativeBackupProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    private String filename(Uri uri){
        if(uri==null||getContext()==null||
            !(getContext().getPackageName()+".backups").equals(uri.getAuthority()))return null;
        String path=uri.getPath();
        if("/physical.json".equals(path))return "physical.json";
        if("/arena.json".equals(path))return "arena.json";
        return null;
    }
    private File file(Uri uri)throws FileNotFoundException {
        String name=filename(uri);
        if(name==null)throw new FileNotFoundException("Unsupported backup URI");
        return new File(new File(getContext().getCacheDir(),"native-backups"),name);
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {
        if(!"r".equals(mode))throw new FileNotFoundException("Read-only export");
        File source=file(uri);
        if(!source.isFile())throw new FileNotFoundException("Backup not staged");
        return ParcelFileDescriptor.open(source,ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri) {return filename(uri)==null?null:"application/json";}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){
        if(filename(uri)==null)return null;
        String[] columns=projection==null?new String[]{"_display_name","_size"}:projection;
        MatrixCursor cursor=new MatrixCursor(columns);
        Object[] values=new Object[columns.length];
        try{
            File source=file(uri);
            for(int i=0;i<columns.length;i++){
                if("_display_name".equals(columns[i]))values[i]="ArcaneVault-"+filename(uri);
                if("_size".equals(columns[i]))values[i]=source.isFile()?source.length():0L;
            }
            cursor.addRow(values);
        }catch(FileNotFoundException ignored){}
        return cursor;
    }
    @Override public Uri insert(Uri uri,ContentValues values){
        throw new UnsupportedOperationException("Backup export is read-only");
    }
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){
        throw new UnsupportedOperationException("Backup export is read-only");
    }
    @Override public int delete(Uri uri,String selection,String[] args){
        throw new UnsupportedOperationException("Backup export is read-only");
    }
}
