package daikon.split;

import daikon.*;

import java.util.*;

import utilMDE.*;

// SplitterLisp maps from a program point name to an array of Splitter
// objects that should be used when splitting that program point.
// It's a shame to have to hard-code for each program point name.

public abstract class SplitterList {

  static HashMap ppt_splitters = new HashMap();

  public static void put(String pptname, Splitter[] splits) {
    // System.out.println("SplitterList.put(" + pptname + ")");
    Assert.assert(! ppt_splitters.containsKey(pptname));
    ppt_splitters.put(pptname, splits);
  }

  public static Splitter[] get_raw(String pptname) {
    return (Splitter[]) ppt_splitters.get(pptname);
  }

  // This routine tries the name first, then the base of the name, then the
  // class.  For instance, if the program point name is
  // "Foo.bar(IZ)V:::EXIT2", then it tries, in order:
  //   "Foo.bar(IZ)V:::EXIT2"
  //   "Foo.bar(IZ)V"
  //   "Foo.bar"
  //   "Foo"

  public static Splitter[] get(String name) {
    String orig_name = name;
    Splitter[] result;
    result = get_raw(name);
    if (Global.debugPptSplit)
      System.out.println("SplitterList.get found "
                         + ((result == null) ? "no" : "" + result.length)
                         + " splitters for " + name);
    if (result != null)
      return result;
    {
      int tag_index = name.indexOf(FileIO.ppt_tag_separator);
      if (tag_index != -1) {
        name = name.substring(0, tag_index);
        result  = get_raw(name);
        if (Global.debugPptSplit)
          System.out.println("SplitterList.get found "
                             + ((result == null) ? "no" : "" + result.length)
                             + " splitters for " + name);
        if (result != null)
          return result;
      }
    }
    {
      int lparen_index = name.indexOf("(");
      if (lparen_index != -1) {
        name = name.substring(0, lparen_index);
        result  = get_raw(name);
        if (Global.debugPptSplit)
          System.out.println("SplitterList.get found "
                             + ((result == null) ? "no" : "" + result.length)
                             + " splitters for " + name);
        if (result != null)
          return result;
      }
    }
    {
      int dot_index = name.indexOf(".");
      if (dot_index != -1) {
        name = name.substring(0, dot_index);
        result  = get_raw(name);
        if (Global.debugPptSplit)
          System.out.println("SplitterList.get found "
                             + ((result == null) ? "no" : "" + result.length)
                             + " splitters for " + name);
        if (result != null)
          return result;
      }
    }

    return null;
  }

}
