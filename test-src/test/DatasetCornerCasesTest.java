package test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import hex.rf.DRF;
import hex.rf.Model;
import hex.rf.Tree.StatType;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.parser.ParseDataset;
import water.util.KeyUtil;

public class DatasetCornerCasesTest {

  @BeforeClass public static void setupCloud() {
    H2O.main(new String[] { });
    long start = System.currentTimeMillis();
    while (System.currentTimeMillis() - start < 5000) {
      if (H2O.CLOUD.size() > 2) break;
      try { Thread.sleep(100); } catch( InterruptedException ie ) {}
    }
    assertEquals("Cloud size of 3", 3, H2O.CLOUD.size());
  }

  /*
   * HTWO-87 bug test
   *
   *  - two lines dataset (one line is a comment) throws assertion java.lang.AssertionError: classOf no dists > 0? 1
   */
  @Test public void testTwoLineDataset() {
    Key fkey = KeyUtil.load_test_file("smalldata/test/HTWO-87-two-lines-dataset.csv");
    Key okey = Key.make("HTWO-87-two-lines-dataset.hex");
    ParseDataset.parse(okey,DKV.get(fkey));
    UKV.remove(fkey);
    ValueArray val = (ValueArray) DKV.get(okey);

    // Check parsed dataset
    assertEquals("Number of chunks == 1", 1, val.chunks());
    assertEquals("Number of rows   == 1", 1, val.num_rows());
    assertEquals("Number of cols   == 9", 9, val.num_cols());

    // setup default values for DRF
    int ntrees  = 5;
    int depth   = 30;
    int gini    = StatType.GINI.ordinal();
    int seed =  42;
    StatType statType = StatType.values()[gini];
    final int num_cols = val.num_cols();
    final int classcol = num_cols-1; // For iris: classify the last column
    final int classes = (short)((val.col_max(classcol) - val.col_min(classcol))+1);

    // Start the distributed Random Forest
    try {
      DRF drf = hex.rf.DRF.web_main(val,ntrees,depth,-1.0,statType,seed,classcol,new int[0], Key.make("model"),true);
      // Just wait little bit
      try { Thread.sleep(500); } catch( InterruptedException e ) {}
      // Create incremental confusion matrix
      Model model = new Model(null,drf._treeskey,num_cols,classes);
      assertEquals("Number of classes == 1", 1,  model._classes);
      assertTrue("Number of trees > 0 ", model.size()> 0);
    } catch( DRF.IllegalDataException e ) {
      assertEquals("hex.rf.DRF$IllegalDataException: Number of classes must be >= 2 and <= 65534, found 1",e.toString());
    }
    UKV.remove(okey);
  }

  /* The following tests deal with one line dataset ended by different number of newlines. */

  /*
   * HTWO-87-related bug test
   *
   *  - only one line dataset - guessing parser should recognize it.
   *  - this datasets are ended by different number of \n (0x0A):
   *    - HTWO-87-one-line-dataset-0.csv    - the line is NOT ended by \n
   *    - HTWO-87-one-line-dataset-1.csv    - the line is ended by 1 \n     (0x0A)
   *    - HTWO-87-one-line-dataset-2.csv    - the line is ended by 2 \n     (0x0A 0x0A)
   *    - HTWO-87-one-line-dataset-1dos.csv - the line is ended by \r\n     (0x0D 0x0A)
   *    - HTWO-87-one-line-dataset-2dos.csv - the line is ended by 2 \r\n   (0x0D 0x0A 0x0D 0x0A)
   */
  @Test public void testOneLineDataset() {
    // max number of dataset files
    final String tests[] = {"0", "1unix", "2unix", "1dos", "2dos" };
    final String test_dir    = "smalldata/test/";
    final String test_prefix = "HTWO-87-one-line-dataset-";

    for (int i = 0; i < tests.length; i++) {
      String datasetFilename = test_dir + test_prefix + tests[i] + ".csv";
      String keyname     = test_prefix + tests[i] + ".hex";
      testOneLineDataset(datasetFilename, keyname);
    }
  }

  private void testOneLineDataset(String filename, String keyname) {
    Key fkey = KeyUtil.load_test_file(filename);
    Key okey = Key.make(keyname);
    ParseDataset.parse(okey,DKV.get(fkey));

    ValueArray val = (ValueArray) DKV.get(okey);
    assertEquals(filename + ": number of chunks == 1", 1, val.chunks());
    assertEquals(filename + ": number of rows   == 1", 1, val.num_rows());
    assertEquals(filename + ": number of cols   == 9", 9, val.num_cols());

    UKV.remove(fkey);
    UKV.remove(okey);
  }
}
