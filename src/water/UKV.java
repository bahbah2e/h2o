package water;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * User-View Key/Value Store
 *
 * This class handles user-view keys, and hides ArrayLets from the end user.
 * 
 *
 * @author <a href="mailto:cliffc@0xdata.com"></a>
 * @version 1.0
 */
public abstract class UKV {

  // This put is a top-level user-update, and not a reflected or retried
  // update.  i.e., The User has initiated a change against the K/V store.
  // This is a WEAK update: it is only strongly ordered with other updates to
  // the SAME key on the SAME node.
  static public void put( Key key, Value val ) {
    assert key.user_allowed();
    Value res = DKV.put_return_old(key,val);
    // If the old Value was a large array, we need to delete the leftover
    // chunks - they are unrelated to the new Value which might be either
    // bigger or smaller than the old Value.
    if( res != null && res.type() == 'A' ) {
      ValueArray ary = (ValueArray)res;
      long sz = ary.length();   // Load long-size from the Value field
      final int chunks = (int)((sz+((1L<<20)-1))>>20); // Divide by 1Meg into chunks, rounding up
      // Delete all chunks
      for( int i=0; i<chunks-1; i++ )
        DKV.remove(ary.make_chunkkey(((long)i)<<20));
    }
    if( res != null ) res.free_mem();
    throw new Error("unimplemented: this belongs on home-node puts only");
  }
  
  static public void remove( Key key ) {  
    assert key.user_allowed();
    Value val = DKV.get(key,32); // Get the existing Value, if any
    if( val == null ) return;     // Trivial delete
    if( val.type() == 'A' ) {    // See if this is an Array
      ValueArray ary = (ValueArray)val;
      long sz = ary.length();   // Load long-size from the Value field
      final int chunks = (int)((sz+((1L<<20)-1))>>20); // Divide by 1Meg into chunks, rounding up
      // Delete all chunks
      for( int i=0; i<chunks-1; i++ )
        DKV.remove(ary.make_chunkkey(((long)i)<<20));
    }
    DKV.remove(key);
    throw new Error("unimplemented: this belongs on home-node puts only");
  }

  // User-Weak-Get a Key from the distributed cloud.
  static public Value get( Key key, int len ) {
    assert key.user_allowed();
    Value val = DKV.get(key,len);
    if( val instanceof ValueCode )
      throw new Error("unimplemented: users should get a polite error if they attempt to fetch a ValueCode");
    if( val instanceof ValueArray ) {
      Value vchunk0 = DKV.get(((ValueArray)val).make_chunkkey(0),len);
      if( len >= vchunk0._max )
        throw new Error("unimplemented: users should get a polite error if they attempt to fetch all of a giant value; users should chunk");
      return vchunk0;           // Else just get the prefix asked for
    }
    return val;
  }
  static public Value get( Key key ) { return get(key,Integer.MAX_VALUE); }
  
  static public void put(String s, Value v) { put(Key.make(s), v); }
  static public Value get(String s) { return get(Key.make(s)); }
  static public void remove(String s) { remove(Key.make(s)); }
  
  
  
  // Appends the given set of bytes to the arraylet
  private static void appendArraylet(ValueArray alet, byte[] b) {
    Value lastChunk = DKV.get(alet.make_chunkkey(alet.length() - alet.length() % ValueArray.chunk_size()));
    int offset = 0;
    int remaining = b.length;
    // first update the last chunk 
    int size = (int) Math.min(ValueArray.chunk_size() - lastChunk._max, b.length);
    if( size != 0 )
      DKV.append(lastChunk._key, b, offset, size);
    while( true ) {
      // now add another chunk(s) if needed
      offset += size;
      remaining = remaining - size;
      if( remaining == 0 )
        break;
      size = Math.min(remaining, (int) ValueArray.chunk_size());
      long coffset = UDP.get8(lastChunk._key._kb, 2) + ValueArray.chunk_size();
      lastChunk = new Value(alet.make_chunkkey(coffset), size);
      System.arraycopy(b, offset, lastChunk.mem(), 0, size);
      DKV.put(lastChunk._key, lastChunk);
    }
    // and finally update the arraylet size. 
    ValueArray newalet = new ValueArray(alet._key, alet.length() + b.length);
    // change the UUID so that it is the same
    System.arraycopy(alet.get(), 10, newalet.mem(), 10, alet._max - 10);
    DKV.put(newalet._key, newalet);
  }

  /**
   * Appends the bytes in b to value stored in k.
   *
   * @param k
   * @param b
   */
  public static void append(Key k, byte[] b) {
    Value old = DKV.get(k);
    // it is a first insert, in this case just put us in
    if( old == null ) {
      try {
        ValueArray.read_put_stream(k, new ByteArrayInputStream(b));
      } catch( IOException e ) {
        // pass - this should never happen
      }
      // There already is a value:
    } else {
      // if it is an ICE value, and we can append, do the append 
      if( old.type() == Value.ICE ) {
        if( old._max + b.length <= ValueArray.chunk_size() ) {
          // we can append safely within the arraylet boundary, only append to
          // the value and we are done
          DKV.append(k, b);
          // and return, we are done
          return;
        } else {
          // we cannot append properly - create an arraylet
          ValueArray alet = new ValueArray(k, old._max);
          Value v = new Value(alet.make_chunkkey(0), old._max);
          System.arraycopy(old.get(), 0, v.mem(), 0, v._max);
          DKV.put(alet._key, alet);
          DKV.put(v._key, v);
        }
      }
      // now it is an arraylet, update it accordingly
      Value v = DKV.get(k);
      assert (v instanceof ValueArray);
      appendArraylet((ValueArray) DKV.get(k), b);
    }
  }
  
}
