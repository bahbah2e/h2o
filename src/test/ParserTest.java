package test;

import org.junit.*;

import water.*;
import water.parser.SeparatedValueParser;

public class ParserTest {
  @BeforeClass public static void setupCloud() {
    H2O.main(new String[] {"-test=none"});
  }

  private double[] d(double... ds) { return ds; }
  private final double NaN = Double.NaN;

  private Key[] k(String... data) {
    Key[] keys = new Key[data.length];
    Key k = Key.make();
    ValueArray va = new ValueArray(k, data.length << ValueArray.LOG_CHK, Value.ICE);
    DKV.put(k, va);
    for (int i = 0; i < data.length; ++i) {
      keys[i] = va.make_chunkkey(i << ValueArray.LOG_CHK);
      DKV.put(keys[i], new Value(keys[i], data[i]));
    }
    return keys;
  }

  @Test public void testBasic() {
    Object[][] t = new Object[][] {
        { "1,2,3",          d(1.0, 2.0, 3.0), },
        { "4,5,6",          d(4.0, 5.0, 6.0), },
        { "4,5.2,",         d(4.0, 5.2, NaN), },
        { ",,",             d(NaN, NaN, NaN), },
        { "asdf,qwer,1",    d(NaN, NaN, 1.0), },
        { "1.1",            d(1.1, NaN, NaN), },
        { "1.1,2.1,3.4",    d(1.1, 2.1, 3.4), },
    };
    int i = 0;
    SeparatedValueParser p;

    StringBuilder sb = new StringBuilder();
    for( i = 0; i < t.length; ++i ) sb.append(t[i][0]).append("\n");

    Key k = Key.make();
    DKV.put(k, new Value(k, sb.toString()));

    p = new SeparatedValueParser(k, ',', 3);
    i = 0;
    for( double[] r : p ) {
      Assert.assertArrayEquals((double[]) t[i++][1], r, 0.0001);
    }

    sb = new StringBuilder();
    for( i = 0; i < t.length; ++i ) sb.append(t[i][0]).append("\r\n");
    DKV.put(k, new Value(k, sb.toString()));

    p = new SeparatedValueParser(k, ',', 3);
    i = 0;
    for( double[] r : p ) {
      Assert.assertArrayEquals((double[]) t[i++][1], r, 0.0001);
    }
  }

  @Test public void testChunkBoundaries() {
    String[] data = new String[] {
        "1,2", ",3\n",
        "2,3,", "4\n",
        "3,4,5"
    };
    double[][] exp = new double[][] {
        d(1.0, 2.0, 3.0),
        d(2.0, 3.0, 4.0),
        d(3.0, 4.0, 5.0),
    };

    Key[] keys = k(data);

    int i = 0;
    SeparatedValueParser p;

    p = new SeparatedValueParser(keys[0], ',', 3);
    for( double[] r : p ) {
      Assert.assertArrayEquals(exp[i++], r, 0.0001);
    }
    Assert.assertEquals(1, i);
    p = new SeparatedValueParser(keys[1], ',', 3);
    for( double[] r : p ) {
      Assert.assertArrayEquals(exp[i++], r, 0.0001);
    }
    Assert.assertEquals(2, i);
    p = new SeparatedValueParser(keys[2], ',', 3);
    for( double[] r : p ) {
      Assert.fail("Key 2 should have skipped the record: 2,3,4 got " + r);
    }
    Assert.assertEquals(2, i);
    p = new SeparatedValueParser(keys[3], ',', 3);
    for( double[] r : p ) {
      Assert.assertArrayEquals(exp[i++], r, 0.0001);
    }
    Assert.assertEquals(3, i);
    Assert.assertEquals(3, exp.length);
    p = new SeparatedValueParser(keys[4], ',', 3);
    for( double[] r : p ) {
      Assert.fail("Key 4 should have skipped the record: 3,4,5 got " + r);
    }
    Assert.assertEquals(5, keys.length);
  }

  @Test public void testChunkBoundariesMixedLineEndings() {
    String[] data = new String[] {
        "1,2", ",3\n",
        "2,3,", "4\n",
        "3,4,5\r\n",
        "4,5,6", "\r\n",
        "5", ",6,", "7\r\n",
        "6,7,8\r\n" +
        "7,8,9\r\n" +
        "8,9", ",10\r\n" +
        "9,10,11\n" +
        "10,11,12",
        "\n11,12,13", "\n" +
        "12,13,14\n" +
        "13,14,15\n" +
        "14,15,16\r", "\n" +
        "15,16,17\n" +
        "16,17,18"
    };
    double[][] exp = new double[][] {
        d(1,  2,  3),
        d(2,  3,  4),
        d(3,  4,  5),
        d(4,  5,  6),
        d(5,  6,  7),
        d(6,  7,  8),
        d(7,  8,  9),
        d(8,  9,  10),
        d(9,  10, 11),
        d(10, 11, 12),
        d(11, 12, 13),
        d(12, 13, 14),
        d(13, 14, 15),
        d(14, 15, 16),
        d(15, 16, 17),
        d(16, 17, 18),
    };

    Key[] keys = k(data);

    int i = 0;
    SeparatedValueParser p;
    for( Key k : keys ) {
      p = new SeparatedValueParser(k, ',', 3);
      for( double[] r : p ) {
        Assert.assertArrayEquals(exp[i++], r, 0.0001);
      }
    }
    Assert.assertEquals(exp.length, i);
  }
}