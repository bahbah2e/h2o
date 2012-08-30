package water.web;

import java.util.Properties;
import water.DKV;
import water.Key;
import water.Value;
import water.ValueArray;

/**
 * Utility holding common code for checking errors on servlet request pages
 * 
 * @author alex@0xdata.com
 */
public class ServletUtil {

  // Pull out two parameters and check for errors on the key
  public static String serveTwoParams(Properties args, RunnableTask task) {
    Object res = check_key(args,"Key");
    if( res instanceof String ) return (String)res;
    Key key = (Key)res;
    // Distributed get
    Value val = DKV.get(key);
    if( val == null )
      return H2OPage.wrap(H2OPage.error("Key not found: "+ key));
    if( !(val instanceof ValueArray) ||
        ((ValueArray)val).num_cols() == 0 )
      return H2OPage.wrap(H2OPage.error("Key not a structured (parsed) array"));
    ValueArray ary = (ValueArray)val;

    int colA = H2OPage.getAsNumber(args,"colA",0);
    int colB = H2OPage.getAsNumber(args,"colB",1);

    if( !(0 <= colA && colA < ary.num_cols()) )
      return H2OPage.wrap(H2OPage.error("Column "+colA+" must be between 0 and "+(ary.num_cols()-1)));
    if( !(0 <= colB && colB < ary.num_cols()) )
      return H2OPage.wrap(H2OPage.error("Column "+colB+" must be between 0 and "+(ary.num_cols()-1)));

    return task.run(ary,colA,colB);
  }

  public static Object check_key(Properties args, String s) {
    String skey = args.getProperty(s);
    if( skey == null ) return H2OPage.wrap(H2OPage.error("Missing argument key: "+ s));
    // Parse the Key & validate it
    try {
      Key key = Key.make(skey); // Get a Key from a raw byte array, if any
      if( !key.user_allowed() )
        return H2OPage.wrap(H2OPage.error("Not a user key: "+ skey));
      return key;
    } catch( IllegalArgumentException e ) {
      return H2OPage.wrap(H2OPage.error("Not a valid key: "+ skey));
    }
  }
  
  // Task to pass the static task execution function
  public static abstract class RunnableTask {
    public abstract String run(ValueArray ary, int colA, int colB);
  }
}