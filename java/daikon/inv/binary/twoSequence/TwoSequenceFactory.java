package daikon.inv.binary.twoSequence;

import daikon.*;
import daikon.inv.*;
import daikon.inv.binary.twoScalar.*;

import utilMDE.*;

import org.apache.log4j.Category;

import java.util.*;

public final class TwoSequenceFactory {

  /** 
   * Debugging logger
   **/
  public static final Category debug = Category.getInstance (TwoSequenceFactory.class.getName());

  // Add the appropriate new Invariant objects to the specified Invariants
  // collection.
  public static Vector instantiate(PptSlice ppt, int pass) {
    Assert.assert(ppt.arity == 2);
    // Not really the right place for these tests
    VarInfo var1 = ppt.var_infos[0];
    VarInfo var2 = ppt.var_infos[1];

    Assert.assert((var1.rep_type == ProglangType.INT_ARRAY)
                  && (var2.rep_type == ProglangType.INT_ARRAY));

    if (! var1.compatible(var2))
      return null;

    VarInfo super1 = var1.isDerivedSubSequenceOf();
    if (super1 == null)
      super1 = var1;
    VarInfo super2 = var2.isDerivedSubSequenceOf();
    if (super2 == null)
      super2 = var2;

    if (debug.isDebugEnabled()) {
      debug.debug ("Instantiating for pass " + new Integer(pass) + " and ppt " + ppt.name);
      debug.debug ("name1 " + super1.getDebugString());
      debug.debug ("name2 " + super2.getDebugString());
    }


    Vector result = new Vector();
    if (pass == 1) {
      // This was test disabled because it resulted in preventing a comparison for
      // this.theArray[this.front..], this.theArray[orig(this.front)+1..]
      // which are actually equal.
      // I decided that the latter shouldn't even be generated -- we should
      // know the relationship between "this.front" and
      // "orig(this.front)+1" -- and re-enabled the test.
      if (super1 == super2) {
        Global.implied_false_noninstantiated_invariants++;
        // System.out.println("No SeqComparison because same super for " + ppt.name);
        LinearBinary lb = LinearBinary.find(ppt);
        if (lb != null)
          System.out.println("  " + lb.format());
      } else {
        result.add(SeqComparison.instantiate(ppt));
      }
    } else if (pass == 2) {
      result.add(Reverse.instantiate(ppt));
      if (super1 == super2) {
        Global.subexact_noninstantiated_invariants += 2;
        Global.implied_false_noninstantiated_invariants += 2 + 2 * Functions.unaryFunctions.length;
      } else {
        Assert.assert(Intern.isInterned(super1.name));
        Assert.assert(Intern.isInterned(super2.name));
        // If the variables (super1 and super2) are different, then their
        // names must be different, too.  In other words. no two distinct
        // variables have the same names.

        Assert.assert(super1.name != super2.name);

        // NonEqual.instantiate(ppt);
        result.add(SubSequence.instantiate(ppt));

        result.add(PairwiseIntComparison.instantiate(ppt));
        result.add(PairwiseLinearBinary.instantiate(ppt));
        for (int i=0; i<2; i++) {
          boolean invert = (i==1);
          VarInfo arg = (invert ? var1 : var2);
          // Don't bother to check arg.isConstant():  we really want to
          // know whether the elements of arg are constant.
          for (int j=0; j<Functions.unaryFunctions.length; j++) {
            result.add(PairwiseFunctionUnary.instantiate(ppt, Functions.unaryFunctionNames[j], Functions.unaryFunctions[j], invert));
          }
        }
      }
    }
    return result;
  }

  private TwoSequenceFactory() {
  }

}
