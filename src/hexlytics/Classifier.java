package hexlytics;

import java.io.Serializable;

/** A classifier can simply decide on the class of the data row that is given to
 * it in the classify() method. The class is returned as an integer starting
 * from 0. 
 *
 * @author peta
 */
public interface Classifier extends Serializable {
  /** Returns the class of the current row data row. */
  int classify(Data data);     
  /** Returns the number of classes for this classifier. */
  int numClasses();
  
  static public class Const implements Classifier {
    private static final long serialVersionUID = -3705740604051055127L;
    final int result;
    /** Creates the constant classifier that will always return the given result.  */
    public Const(int result) { this.result = result; }
    public int classify(Data row) { return result; }
    /** The ConstClassifier always classifies to only a single class.  */
    public int numClasses() { return 1; }    
  }
  
  public static class Random implements Classifier {
    final double probs[];   
    public int classify(Data data) {
      double x = new java.util.Random().nextDouble();
      for (int i = 0; i< probs.length; ++i) {
        x = x - probs[i]; if (x<0) return i;
      }
      return probs.length-1;
    }
    public int numClasses() { return probs.length; }
    public Random(double[] dist) {
      probs = new double[dist.length];
      double s = 0;
      for (double d: dist) s+= d;
      for (int i = 0; i< probs.length; ++i) probs[i] = dist[i]/s;
    }   
  }
  
  public static class Operations {    
    /** Returns the standard error of the given classifier on the given dataset.
     * Note that for the RF implementation: this error does not look at Bag/OutofBag.
     * This means that for RF the error reported will be smaller than the actual
     * error rate. If we wanted a more accurate error rate we could drop the inBag
     * values.     */ 
    public static double error(Classifier c, Data d) {
      double err = 0.0;
      double wsum = 0.0;
      for (int r = 0; r < d.rows(); ++r) {
        d.seek(r);
        wsum += d.weight();
        if (d.classOf() != c.classify(d)) err += d.weight();
      }
      return err / wsum;
    }    
  }  
}
