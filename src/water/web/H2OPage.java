package water.web;

import java.util.Properties;

import water.Key;

/** H2O branded web page.
 *
 * We might in theory not need this and it can be all pushed to Page, but this
 * is just for the sake of generality.
 *
 * @author peta
 */
public abstract class H2OPage extends Page {

  protected int _refresh = 0;

  protected abstract String serveImpl(Server server, Properties args) throws PageError;

  private static final String html =
      "<!DOCTYPE html>"
    + "<html lang=\"en\">"
    + "  <head>"
    + "    <meta charset=\"utf-8\">"
    + "    %refresh"
    + "    <title>H2O, from 0xdata</title>"
    + "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
    + "    <link href=\"bootstrap/css/bootstrap.css\" rel=\"stylesheet\">"
    + "    <style>"
    + "      body {"
    + "        padding-top: 60px; /* 60px to make the container go all the way to the bottom of the topbar */"
    + "      }"
    + "    </style>"
    + "    <link href=\"bootstrap/css/bootstrap-responsive.css\" rel=\"stylesheet\">"
    + "    <!-- Le HTML5 shim, for IE6-8 support of HTML5 elements -->"
    + "    <!--[if lt IE 9]>"
    + "      <script src=\"http://html5shim.googlecode.com/svn/trunk/html5.js\"></script>"
    + "    <![endif]-->"
    + "    <!-- Le fav and touch icons -->"
    + "    <link rel=\"shortcut icon\" href=\"favicon.ico\">"
    + "    <script src='bootstrap/js/jquery.js'></script>"
    + "  </head>"
    + "  <body>"
    + "    <div class=\"navbar navbar-fixed-top\">"
    + "      <div class=\"navbar-inner\">"
    + "        <div class=\"container\">"
    + "          <a class=\"btn btn-navbar\" data-toggle=\"collapse\" data-target=\".nav-collapse\">"
    + "            <span class=\"icon-bar\"></span>"
    + "            <span class=\"icon-bar\"></span>"
    + "            <span class=\"icon-bar\"></span>"
    + "          </a>"
    + "          <a class=\"brand\" href=\"#\">H<sub>2</sub>O</a>"
    + "          <div class=\"nav\">"
    + "            <ul class=\"nav\">"
    + "              <li><a href=\"/\">Cloud</a></li>"
    + "              <li><a href=\"/StoreView\">Node</a></li>"
    + "              <li><a href=\"/GetQuery\">Get</a></li>"
    + "              <li><a href=\"/Put\">Put</a></li>"
    + "              <li><a href=\"/Timeline\">Timeline</a></li>"
    + "              <li><a href=\"/ImportQuery\">Import</a></li>"
    + "              <li><a href=\"/DebugView\">Debug View</a></li>"
    + "              <li><a href=\"/ProgressView\">Progress View</a></li>"
    + "              <li><a href=\"/Network\">Network</a></li>"
    + "              <li><a href=\"/Shutdown\">Shutdown All</a></li>"
    + "            </ul>"
    + "          </div><!--/.nav-collapse -->"
    + "        </div>"
    + "      </div>"
    + "    </div>"
    + "    <div class=\"container\">"
    + "%contents"
    + "    </div>"
    + "  </body>"
    + "</html>"
    ;

  @Override public String serve(Server server, Properties args) {
    RString response = new RString(html);
    try {
      String result = serveImpl(server, args);
      if (result == null) return result;
      if (_refresh!=0) response.replace("refresh","<META HTTP-EQUIV='refresh' CONTENT='"+_refresh+"'>");
      response.replace("contents",result);
    } catch (PageError e) {
      response.replace("contents", e._msg);
    }
    return response.toString();
  }


  private static final String html_notice =
            "<div class='alert %atype'>"
          + "%notice"
          + "</div>"
          ;

  public static String error(String text) {
    RString notice = new RString(html_notice);
    notice.replace("atype","alert-error");
    notice.replace("notice",text);
    return notice.toString();
  }

  public static String success(String text) {
    RString notice = new RString(html_notice);
    notice.replace("atype","alert-success");
    notice.replace("notice",text);
    return notice.toString();
  }

  private static final char[] HEX = "0123456789abcdef".toCharArray();

  public static String encode(Key k) {
    byte[] what = k._kb;
    int len = what.length;
    while( --len >= 0 ) {
      char a = (char) what[len];
      if( a == '-' ) continue;
      if( a == '.' ) continue;
      if( 'a' <= a && a <= 'z' ) continue;
      if( 'A' <= a && a <= 'Z' ) continue;
      if( '0' <= a && a <= '9' ) continue;
      break;
    }
    StringBuilder sb = new StringBuilder();
    for( int i = 0; i <= len; ++i ) {
      byte a = what[i];
      sb.append(HEX[(a >> 4) & 0x0F]);
      sb.append(HEX[(a >> 0) & 0x0F]);
    }
    sb.append("____");
    for( int i = len + 1; i < what.length; ++i ) sb.append((char)what[i]);
    return sb.toString();
  }

  public static Key decode(String what) {
    int len = what.indexOf("____");
    String tail = what.substring(len + 4);
    int r = 0;
    byte[] res = new byte[len/2 + tail.length()];
    for( int i = 0; i < len; i+=2 ) {
      char h = what.charAt(i);
      char l = what.charAt(i+1);
      h -= Character.isDigit(h) ? '0' : ('a' - 10);
      l -= Character.isDigit(l) ? '0' : ('a' - 10);
      res[r++] = (byte)(h << 4 | l);
    }
    System.arraycopy(tail.getBytes(), 0, res, r, tail.length());
    return Key.make(res);
  }

  public static String wrap(String what) {
    RString response = new RString(html);
    response.replace("contents",what);
    return response.toString();
  }

  public static int getAsNumber(Properties args, String arg, int def) {
    int result = def;
    try {
      String s = args.getProperty(arg,"");
      if (!s.isEmpty())
        result = Integer.parseInt(s);
    } catch (NumberFormatException e) {
      return def;
    }
    return result;
  }
}
