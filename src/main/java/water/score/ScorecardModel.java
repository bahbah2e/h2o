package water.score;

import java.util.*;
import javassist.*;
import water.*;
import java.lang.reflect.Constructor;

/**
 * Scorecard model - decision table.
 */
public class ScorecardModel extends ScoreModel {
  /** Initial score */
  final double _initialScore;
  /** The rules to each for each feature, they map 1-to-1 with the Model's
   *  column list.  */
  final RuleTable _rules[];

  /** Score this model on the specified row of data.  */
  public double score(final HashMap<String, Comparable> row ) {
    // By default, use the scoring interpreter.  The Builder JITs a new
    // subclass with an overloaded 'score(row)' call which has a JIT'd version
    // of the rules.  i.e., calling 'score(row)' on the returned ScorecardModel
    // instance runs the fast version, but you can cast to the base version if
    // you want the interpreter.
    return score_interpreter(row);
  }
  // Use the rule interpreter
  public double score_interpreter(final HashMap<String, Comparable> row ) {
    double score = _initialScore;
    for( int i=0; i<_rules.length; i++ )
      score += _rules[i].score(row.get(_colNames[i]));
    return score;
  }
  public double score(int[] MAP, String[] SS, double[] DS) {
    return score_interpreter(MAP,SS,DS);
  }
  private double score_interpreter(int[] MAP, String[] SS, double[] DS) {
    double score = _initialScore;
    for( int i=0; i<_rules.length; i++ ) {
      int idx = MAP[i];
      String ss = idx==-1 ? null       : SS[idx];
      double dd = idx==-1 ? Double.NaN : DS[idx];
      double s = _rules[i].score(ss,dd);
      score += s;
    }
    return score;
  }

  // JIT a score method with signature 'double score(HashMap row)'
  public void makeScoreHashMethod(CtClass scClass) {
    // Map of previously extracted PMML names, and their java equivs
    HashMap<String,String> vars = new HashMap<String,String>();
    StringBuilder sb = new StringBuilder();
    sb.append("double score( java.util.HashMap row ) {\n"+
              "  double score = "+_initialScore+";\n");
    try {
      for( int i=0; i<_rules.length; i++ )
        _rules[i].makeFeatureHashMethod(sb,vars,scClass);
      sb.append("  return score;\n}\n");
      CtMethod happyMethod = CtMethod.make(sb.toString(),scClass);
      scClass.addMethod(happyMethod);
    } catch( Exception re ) {
      System.err.println("=== crashing ===");
      System.err.println(sb.toString());
      throw new Error(re);
    } finally {
    }
  }

  public void makeScoreAryMethod(CtClass scClass) {
    // Map of previously extracted PMML names, and their java equivs
    HashMap<String,String> vars = new HashMap<String,String>();
    StringBuilder sb = new StringBuilder();
    sb.append("double score( int[] MAP, java.lang.String[] SS, double[] DS ) {\n"+
              "  double score = "+_initialScore+";\n");
    try {
      for( int i=0; i<_rules.length; i++ )
        _rules[i].makeFeatureAryMethod(sb,vars,scClass,i);
      sb.append("  return score;\n}\n");

      CtMethod happyMethod = CtMethod.make(sb.toString(),scClass);
      scClass.addMethod(happyMethod);

    } catch( Exception re ) {
      System.err.println("=== crashing ===");
      System.err.println(sb.toString());
      throw new Error(re);
    } finally {
    }
  }

  // Return the java-equivalent from the PMML variable name, creating and
  // installing it as needed.  If the value is created, we also emit Java code
  // to emit it at runtime.
  public static String getName( String pname, DataTypes type, StringBuilder sb ) {
    String jname = xml2jname(pname);

    // Emit the code to do the load
    return jname;
  }

  /** Feature decision table */
  public static class RuleTable {
    final String _name;
    final Rule[] _rule;
    final DataTypes _type;

    public RuleTable(final String name, final DataTypes type, final Rule[] decisions) { _name = name; _type = type; _rule = decisions; 
      if( name.startsWith("i") )
        System.out.println(type);
      assert type != null;
    }

    public void makeFeatureHashMethod( StringBuilder sbParent, HashMap<String,String> vars, CtClass scClass ) {
      String jname = xml2jname(_name);
      StringBuilder sb = new StringBuilder();
      sb.append("double ").append(jname).append("( java.util.HashMap row ) {\n"+
                "  double score = 0;\n");
      switch( _type ) {
      case STRING : sb.append("  String " ); break;
      case BOOLEAN: sb.append("  double "); break;
      default     : sb.append("  double " ); break;
      }
      sb.append(jname);
      switch( _type ) {
      case STRING : sb.append(" = getString (row,\""); break;
      case BOOLEAN: sb.append(" = getBoolean(row,\"" ); break;
      default     : sb.append(" = getNumber (row,\""  ); break;
      }
      sb.append(_name).append("\");\n");
      sb.append("  if( false ) ;\n");
      for (Rule r : _rule)
        if( _type == DataTypes.STRING) r.toJavaStr(sb,jname);
        else if( _type == DataTypes.BOOLEAN) r.toJavaBool(sb,jname);
        else r.toJavaNum(sb,jname);
      // close the dangling 'else' from all the prior rules
      sb.append("  return score;\n}\n");
      sbParent.append("  score += ").append(jname).append("(row);\n");

      // Now install the method
      try {
        CtMethod happyMethod = CtMethod.make(sb.toString(),scClass);
        scClass.addMethod(happyMethod);
      } catch( Exception re ) {
        System.err.println("=== crashing ===");
        System.err.println(sb.toString());
        throw new Error(re);
      } finally {
      }
    }

    public void makeFeatureAryMethod( StringBuilder sbParent, HashMap<String,String> vars, CtClass scClass, int fidx ) {
      String jname = xml2jname(_name);
      StringBuilder sb = new StringBuilder();
      sb.append("double ").append(jname);
      sb.append("( int[]MAP, java.lang.String[]SS, double[]DS ) {\n"+
                "  double score = 0;\n"+
                "  int didx=MAP[").append(fidx).append("];\n");
      switch( _type ) {
      case STRING : sb.append("  String " ); break;
      case BOOLEAN: sb.append("  boolean "); break;
      default     : sb.append("  double " ); break;
      }
      sb.append(jname);
      switch( _type ) {
      case STRING : sb.append(" = didx==-1 ? null : SS[didx];\n"); break;
      case BOOLEAN: sb.append(" = didx==-1 ? false : DS[didx]==1.0;\n"); break;
      default     : sb.append(" = didx==-1 ? Double.NaN : DS[didx];\n" ); break;
      }
      sb.append("  if( false ) ;\n");
      for (Rule r : _rule)
        if( _type == DataTypes.STRING) r.toJavaStr(sb,jname);
        else if( _type == DataTypes.BOOLEAN) r.toJavaBool(sb,jname);
        else r.toJavaNum(sb,jname);
      // close the dangling 'else' from all the prior rules
      sb.append("  return score;\n}\n");
      sbParent.append("  score += ").append(jname).append("(MAP,SS,DS);\n");

      // Now install the method
      try {
        CtMethod happyMethod = CtMethod.make(sb.toString(),scClass);
        scClass.addMethod(happyMethod);

      } catch( Exception re ) {
        System.err.println("=== crashing ===");
        System.err.println(sb.toString());
        throw new Error(re);
      } finally {
      }
    }

    // The rule interpreter
    double score(Object value) {
      double score = 0;
      for (Rule r : _rule) {
        if( r.match(value) ) {
          score += r._score;
          break;
        }
      }
      return score;
    }

    double score(String s, double d) {
      double score = 0;
      for (Rule r : _rule) {
        if( r.match(s,d) ) {
          score += r._score;
          break;
        }
      }
      return score;
    }

    @Override
    public String toString() {
      return "RuleTable [_name=" + _name + ", _rule=" + Arrays.toString(_rule) + ", _type=" + _type + "]";
    }
  }

  /** Scorecard decision rule */
  public static class Rule {
    final double _score;
    final Predicate _predicate;
    public Rule(double score, Predicate pred) { _score = score; _predicate = pred; }
    boolean match(Object value) { return _predicate.match(value); }
    boolean match(String s, double d) { return _predicate.match(s,d); }
    @Override public String toString() { return _predicate.toString() + " => " + _score; }
    public StringBuilder toJavaNum( StringBuilder sb, String jname ) {
      sb.append("  else if( ");
      return _predicate.toJavaNum(sb,jname).append(" ) score += ").append(_score).append(";\n");
    }
    public StringBuilder toJavaBool( StringBuilder sb, String jname ) {
      sb.append("  else if( ");
      return _predicate.toJavaBool(sb,jname).append(" ) score += ").append(_score).append(";\n");
    }
    public StringBuilder toJavaStr( StringBuilder sb, String jname ) {
      sb.append("  else if( ");
      return _predicate.toJavaStr(sb,jname).append(" ) score += ").append(_score).append(";\n");
    }
  }

  public static abstract class Predicate {
    abstract boolean match(Object value);
    abstract boolean match(String s, double d);
    abstract StringBuilder toJavaNum( StringBuilder sb, String jname );
    StringBuilder toJavaBool( StringBuilder sb, String jname ) { throw H2O.unimpl(); }
    StringBuilder toJavaStr( StringBuilder sb, String jname ) { throw H2O.unimpl(); }
  }

  public static abstract class Comparison extends Predicate {
    public final String _value;
    public final double _num;
    public final double _bool;
    public Comparison(String value) {
      _value = value;
      _num = getNumber(value);
      _bool = getBoolean(value);
    }
  }

  /** Less or equal */
  public static class LessOrEqual extends Comparison {
    public LessOrEqual(String value) { super(value); }
    @Override boolean match(Object value) {
      if( !Double.isNaN(_num) ) return getNumber(value) <= _num;
      if( !Double.isNaN(_bool) ) return getBoolean(value) <= _bool;
      String s = getString(value);
      if( s==null ) return false;
      return _value.compareTo(s) >= 0;
    }
    @Override boolean match(String s, double d) { return d <= _num; }
    @Override public String toString() { return "X<=" + _value; }
    @Override public StringBuilder toJavaNum( StringBuilder sb, String jname ) {
      return sb.append(jname).append("<=").append(_num);
    }
  }

  public static class LessThan extends Comparison {
    public LessThan(String value) { super(value); }
    @Override boolean match(Object value) {
      if( !Double.isNaN(_num) ) return getNumber(value) < _num;
      if( !Double.isNaN(_bool) ) return getBoolean(value) < _bool;
      String s = getString(value);
      if( s==null ) return false;
      return _value.compareTo(s) > 0;
    }
    @Override boolean match(String s, double d) { return d < _num; }
    @Override public String toString() { return "X<" + _value; }
    @Override public StringBuilder toJavaNum( StringBuilder sb, String jname ) {
      return sb.append(jname).append("<").append(_num);
    }
  }

  public static class GreaterOrEqual extends Comparison {
    public GreaterOrEqual(String value) { super(value); }
    @Override boolean match(Object value) {
      if( !Double.isNaN(_num) ) return getNumber(value) >= _num;
      if( !Double.isNaN(_bool) ) return getBoolean(value) >= _bool;
      String s = getString(value);
      if( s==null ) return false;
      return _value.compareTo(s) <= 0;
    }
    @Override boolean match(String s, double d) { return d >= _num; }
    @Override public String toString() { return "X>=" + _value; }
    @Override public StringBuilder toJavaNum( StringBuilder sb, String jname ) {
      return sb.append(jname).append(">=").append(_num);
    }
  }

  public static class GreaterThan extends Comparison {
    public GreaterThan(String value) { super(value); }
    @Override boolean match(Object value) {
      if( !Double.isNaN(_num) ) return getNumber(value) > _num;
      if( !Double.isNaN(_bool) ) return getBoolean(value) > _bool;
      String s = getString(value);
      if( s==null ) return false;
      return _value.compareTo(s) < 0;
    }
    @Override boolean match(String s, double d) { return d > _num; }
    @Override public String toString() { return "X>" + _value; }
    @Override public StringBuilder toJavaNum( StringBuilder sb, String jname ) {
      return sb.append(jname).append(">").append(_num);
    }
  }

  public static class IsMissing extends Predicate {
    @Override boolean match(Object value) { return value==null; }
    @Override boolean match(String s, double d) { return Double.isNaN(d); }
    @Override public String toString() { return "isMissing"; }
    @Override public StringBuilder toJavaNum( StringBuilder sb, String jname ) {
      return sb.append("Double.isNaN("+jname+")");
    }
    @Override public StringBuilder toJavaBool( StringBuilder sb, String jname ) {
      return sb.append("Double.isNaN("+jname+")");
    }
    @Override public StringBuilder toJavaStr( StringBuilder sb, String jname ) {
      return sb.append(jname).append("==null");
    }
  }

  public static class Equals extends Comparison {
    public Equals(String value) { super(value); }
    @Override boolean match(Object value) {
      if( !Double.isNaN(_num) ) return getNumber(value) == _num;
      if( !Double.isNaN(_bool) ) return getBoolean(value) == _bool;
      String s = getString(value);
      if( s==null ) return false;
      return _value.compareTo(s) == 0;
    }
    @Override boolean match(String s, double d) {
      if( !Double.isNaN(_num) ) return d == _num;
      if( !Double.isNaN(_bool) ) return d == _bool;
      return _value.equals(s);
    }
    @Override public String toString() { return "X==" + _value; }
    @Override public StringBuilder toJavaNum( StringBuilder sb, String jname ) {
      return sb.append(jname).append("==").append(_num);
    }
    @Override StringBuilder toJavaBool( StringBuilder sb, String jname ) {
      return sb.append(jname).append("==").append(_bool);
    }
    @Override StringBuilder toJavaStr( StringBuilder sb, String jname ) {
      return sb.append("\"").append(_value).append("\".equals(").append(jname).append(")");
    }
  }

  public static abstract class CompoundPredicate extends Predicate {
    Predicate _l,_r;
    public final void add(Predicate pred) {
      assert _l== null || _r==null : "Predicate already filled";
      if (_l==null) _l = pred; else _r = pred;
    }
    @Override public StringBuilder toJavaNum( StringBuilder sb, String jname ) { throw H2O.unimpl(); }
    public StringBuilder makeNum(StringBuilder sb, String jname, String rel) {
      sb.append("(");
      _l.toJavaNum(sb,jname);
      sb.append(" ").append(rel).append(" ");
      _r.toJavaNum(sb,jname);
      sb.append(")");
      return sb;
    }
    public StringBuilder makeStr(StringBuilder sb, String jname, String rel) {
      sb.append("(");
      _l.toJavaStr(sb,jname);
      sb.append(" ").append(rel).append(" ");
      _r.toJavaStr(sb,jname);
      sb.append(")");
      return sb;
    }
  }
  public static class And extends CompoundPredicate {
    @Override final boolean match(Object value) { return _l.match(value) && _r.match(value); }
    @Override final boolean match(String s, double d) { return _l.match(s,d) && _r.match(s,d); }
    @Override public String toString() { return "(" + _l.toString() + " and " + _r.toString() + ")"; }
    @Override public StringBuilder toJavaNum( StringBuilder sb, String jname ) { return makeNum(sb,jname,"&&"); }
    @Override public StringBuilder toJavaStr( StringBuilder sb, String jname ) { return makeStr(sb,jname,"&&"); }
  }
  public static class Or extends CompoundPredicate {
    @Override final boolean match(Object value) { return _l.match(value) || _r.match(value); }
    @Override final boolean match(String s, double d) { return _l.match(s,d) || _r.match(s,d); }
    @Override public String toString() { return "(" + _l.toString() + " or " + _r.toString() + ")"; }
    @Override public StringBuilder toJavaNum( StringBuilder sb, String jname ) { return makeNum(sb,jname,"||"); }
    @Override public StringBuilder toJavaStr( StringBuilder sb, String jname ) { return makeStr(sb,jname,"||"); }
  }

  public static class IsIn extends Predicate {
    public String[] _values;
    public IsIn(String[] values) { _values = values; }
    @Override boolean match(Object value) {
      for( String t : _values ) if (t.equals(value)) return true;
      return false;
    }
    @Override boolean match(String s, double d) {
      for( String t : _values ) if (t.equals(s)) return true;
      return false;
    }
    @Override public String toString() {
      String x = "";
      for( String s: _values ) x += s + " ";
      return "X is in {" + x + "}"; }
    @Override public StringBuilder toJavaNum( StringBuilder sb, String jname ) { throw H2O.unimpl(); }
    @Override StringBuilder toJavaStr( StringBuilder sb, String jname ) {
      for( String s : _values )
        sb.append("\"").append(s).append("\".equals(").append(jname).append(") || ");
      return sb.append("false");
    }
  }

  public static class IsNotIn extends IsIn {
    public IsNotIn(String[] values) { super(values); }
    @Override boolean match(Object value) { return ! super.match(value); }
    @Override boolean match(String s, double d) { return ! super.match(s,d); }
    @Override public StringBuilder toJavaNum( StringBuilder sb, String jname ) { throw H2O.unimpl(); }
    @Override StringBuilder toJavaStr( StringBuilder sb, String jname ) {
      sb.append("!(");
      super.toJavaStr(sb,jname);
      return sb.append(")");
    }
  }

  @Override
  public String toString() {
    return super.toString()+", _initialScore=" + _initialScore;
  }

  // Happy Helper Methods for the generated code
  public static double getNumber( HashMap<String,Object> row, String s ) {
    return getNumber(row.get(s));
  }
  public static double getNumber( Object o ) {
    // hint to the jit to do a instanceof breakdown tree
    if( o instanceof Double ) return ((Double)o).doubleValue();
    if( o instanceof Long   ) return ((Long  )o).doubleValue();
    if( o instanceof Number ) return ((Number)o).doubleValue();
    if( o instanceof String ) {
      try { return Double.valueOf((String)o); } catch( Throwable t ) { }
    }
    return Double.NaN;
  }
  public static double getBoolean( HashMap<String,Object> row, String s ) {
    return getBoolean(row.get(s));
  }
  public static double getBoolean( Object o ) {
    if( o instanceof Boolean ) return ((Boolean)o) ? 1.0 : 0.0;
    if( o instanceof String ) {
      try {
        if( "true".equalsIgnoreCase((String) o) ) return 1.0;
        if( "false".equalsIgnoreCase((String) o) ) return 0.0;
      } catch( Throwable t ) { }
    }
    return Double.NaN;
  }
  public static String getString( HashMap<String,Object> row, String s ) {
    return getString(row.get(s));
  }
  public static String getString( Object o ) {
    if( o instanceof String ) return (String)o;
    return o == null ? null : o.toString();
  }


  private ScorecardModel(String name, String[] colNames, double initialScore, RuleTable[] rules) {
    super(name,colNames);
    assert colNames.length==rules.length;
    _initialScore = initialScore;
    _rules = rules;
  }
  protected ScorecardModel(ScorecardModel base) {
    this(base._name,base._colNames,base._initialScore,base._rules);
  }


  /** Scorecard model builder: JIT a subclass with the fast version wired in to
   *  'score(row)' */
  public static ScorecardModel make(final String name, final double initialScore, RuleTable[] rules) {
    // Get the list of features
    String[] colNames = new String[rules.length];
    for( int i=0; i<rules.length; i++ )
      colNames[i] = rules[i]._name;

    // javassist support for rewriting class files
    ClassPool _pool = ClassPool.getDefault();
    try {
      // Make a javassist class in the java hierarchy
      String cname = uniqueClassName(name);
      CtClass scClass = _pool.makeClass(cname);
      CtClass baseClass = _pool.get("water.score.ScorecardModel"); // Full Name Lookup
      scClass.setSuperclass(baseClass);
      // Produce the scoring method(s)
      ScorecardModel scm = new ScorecardModel(name, colNames,initialScore, rules);
      scm.makeScoreHashMethod(scClass);
      scm.makeScoreAryMethod(scClass);
      // Produce a 1-arg constructor
      String cons = "  public "+cname+"(water.score.ScorecardModel base) { super(base); }";
      CtConstructor happyConst = CtNewConstructor.make(cons,scClass);
      scClass.addConstructor(happyConst);

      Class myClass = scClass.toClass(ScorecardModel.class.getClassLoader(), null);
      Constructor<ScorecardModel> co = myClass.getConstructor(ScorecardModel.class);
      ScorecardModel jitted_scm = co.newInstance(scm);
      return jitted_scm;

    } catch( Exception e ) {
      System.err.println("javassist failed: "+e);
      e.printStackTrace();
    }
    return null;
  }

  /** Features datatypes promoted by PMML spec. */
  public enum DataTypes {
    DOUBLE("double"), INT("int"), BOOLEAN("boolean"), STRING("String");
    final String _jname;
    DataTypes( String jname ) { _jname = jname; }
    public static DataTypes parse(String s) {return DataTypes.valueOf(s.toUpperCase()); }
    public String jname() { return _jname; }
  }
}
