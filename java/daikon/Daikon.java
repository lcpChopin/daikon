// Main routine for Daikon invariant detector
// For documentation, see file doc/daikon.html in the distribution.

package daikon;

import daikon.split.*;
import daikon.suppress.*;
import daikon.inv.*;
import daikon.inv.unary.scalar.*;
import daikon.inv.unary.string.*;
import daikon.inv.unary.sequence.*;
import daikon.inv.unary.stringsequence.*;
import daikon.inv.binary.twoScalar.*;
import daikon.inv.binary.twoString.*;
import daikon.inv.binary.twoSequence.*;
import daikon.inv.binary.sequenceScalar.*;
import daikon.inv.binary.sequenceString.*;
import daikon.inv.ternary.threeScalar.*;
import daikon.inv.Invariant.OutputFormat;
import daikon.config.Configuration;

import java.util.*;
import java.io.*;
import java.text.DateFormat;
import java.text.NumberFormat;

import org.apache.oro.text.regex.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import gnu.getopt.*;
import utilMDE.*;

/**
 * The "main" method is the main entry point for the Daikon invariant detector.
 **/
public final class Daikon {
  private Daikon() { throw new Error("do not instantiate"); }

  public static final String release_version = "3.1.0";
  public static final String release_date = "June 1, 2004";
  public static final String release_string
    = "Daikon version " + release_version
    + ", released " + release_date
    + "; http://pag.csail.mit.edu/daikon.";

  // Variables starting with dkconfig_ should only be set via the
  // daikon.config.Configuration interface.
  /**
   * Boolean.  Controls whether conditional program points (see Daikon
   * manual) are displayed.
   **/
  public static boolean dkconfig_output_conditionals = true;

  // Variables starting with dkconfig_ should only be set via the
  // daikon.config.Configuration interface.
  /**
   * Boolean.  Controls whether invariants are reported over floating-point
   * values.
   **/
  public static boolean dkconfig_enable_floats = true;

  /**
   * Boolean.  Just print the total number of possible invariants
   * and exit.
   */
  public static boolean dkconfig_calc_possible_invs;

  /**
   * Integer. Percentage of ppts to process.  All ppts are sorted and
   * the first dkconfig_ppt_perc ppts are included.  A percentage of
   * 100 (default) matches all ppts.
   */
  public static int dkconfig_ppt_perc = 100;

  /**
   * Boolean.  Controls whether or not total samples read and processed
   * are printed at the end of processing.
   */
  public static boolean dkconfig_print_sample_totals = false;

  // All these variables really need to be organized better.

  public static final String lineSep = Global.lineSep;

  /**
   * Boolean.  Controls whether or not splitting based on the built-in
   * splitting rules is disabled.  The built-in rules look for implications
   * based on boolean return values and also when there are exactly two
   * exit points from a method.
   **/
  public static boolean dkconfig_disable_splitting = false;

  // Change this at your peril; high costs in time and space for "false",
  // because so many more invariants get instantiated.
  public static final boolean check_program_types = true;

  // Problem with setting this to true:
  //  get no invariants over any value that can ever be missing
  // Problem with setting this to false:
  //  due to differrent number of samples, IsEqualityComparison is
  //  non-transitive (that is specially handled in the code)
  public static final boolean invariants_check_canBeMissing = false;

  // Specialized version for array elements; only examined if
  // invariants_check_canBeMissing is false
  public static final boolean invariants_check_canBeMissing_arrayelt = true;

  public static final boolean disable_modbit_check_message = false;
  // Not a good idea to set this to true, as it is too easy to ignore the
  // warnings and the modbit problem can cause an error later.
  public static final boolean disable_modbit_check_error = false;

  // When true, don't print textual output.
  public static boolean no_text_output = false;

  // When true, show how much time each program point took.
  // Has no effect unless no_text_output is true.
  public static boolean show_progress = false;

  /**
   * Whether to use the "new" equality set mechanism for handling
   * equality, using canonicals to have instantiation of invariants
   * only over equality sets.
   **/
  public static boolean use_equality_optimization = true;

  /**
   * Whether to use the dynamic constants optimization.  This
   * optimization doesn't instantiate invariants over constant
   * variables (ie, that that have only seen one value).  When the
   * variable receives a second value, invariants are instantiated and
   * are given the sample representing the previous constant value.
   **/
  public static boolean dkconfig_use_dynamic_constant_optimization = true;

  /**
   * If true, no invariants will be guarded.  Guarding means that
   * if a variable "can be missing" in a dtrace file, predicates
   * are attached to invariants ensuring their values can be dereferenced.
   * For instance, if a.b "can be missing", and a.b == 5 is an
   * invariant, then it is more properly declared as (a != null) ==>
   * (a.b == 5).
   **/
  public static boolean dkconfig_noInvariantGuarding = false;

  /**
   * When true compilation errors during splitter file generation
   * will not be reported to the user.
   */
  public static boolean dkconfig_suppressSplitterErrors = false;

  /**
   * When true, invariants that are implied by other invariants are
   * suppressed.
   **/
  public static boolean dkconfig_use_suppression_optimization = true;

  /**
   * Whether suppressed invariants can suppress others.  Eventually
   * this will be obsolete, as we will be able to do cycle detection
   * on invariant dependencies.
   **/
  public static boolean suppress_with_suppressed = true;

  /**
   * How many samples to process per ppt before suppression is
   * initiated.  When zero, initiate suppression immediately before
   * seeing samples.
   **/
  public static int suppress_samples_min = 1000000000;

  /**
   * Whether to associate the program points in a dataflow hierarchy,
   * as via Nimmer's thesis.  Deactivate only for languages and
   * analyses where flow relation is nonsensical.
   **/
  public static boolean use_dataflow_hierarchy = true;

  /**
   * Whether to use the bottom up implementation of the dataflow
   * hierarchy.  This mechanism builds invariants initially
   * only at the leaves of the partial order.  Upper points are
   * calculated by joining the invariants from each of their children
   * points.
   **/
  public static boolean dkconfig_df_bottom_up = true;

  // When true, don't print invariants when their controlling ppt
  // already has them.  For example, this is the case for invariants
  // in public methods which are already given as part of the object
  // invariant for that class.
  public static boolean suppress_implied_controlled_invariants = true;

  // When true, don't print EXIT invariants over strictly orig()
  // variables when the corresponding entry ppt already has the
  // invariant.
  public static boolean suppress_implied_postcondition_over_prestate_invariants = false;

  // When true, use the Simplify theorem prover (not part of Daikon)
  // to locate logically redundant invariants, and flag them as
  // redundant, so that they are removed from the printed output.
  public static boolean suppress_redundant_invariants_with_simplify = false;

  // Set what output style to use.  DAIKON is the default; ESC style
  // is based on JML; SIMPLIFY style uses first order logical
  // expressions with lots of parens
  public static OutputFormat output_style = OutputFormat.DAIKON;
  // public static OutputFormat output_style = OutputFormat.ESCJAVA;
  // public static OutputFormat output_style = OutputFormat.DBCJAVA;
  // public static OutputFormat output_style = OutputFormat.SIMPLIFY;

  // When true, output numbers of values and samples (also names of variables)
  public static boolean output_num_samples = false;

  public static boolean ignore_comparability = false;

  // Controls which program points/variables are used/ignored.
  public static Pattern ppt_regexp;
  public static Pattern ppt_omit_regexp;
  public static Pattern var_omit_regexp;

  /**
   * When true, perform detailed internal checking.
   * These are essentially additional, possibly costly assert statements.
   */
  public static boolean dkconfig_internal_check = true;

  /**
   * If set, only ppts less than ppt_max_name are included.  Used by the
   * configuration option dkconfig_ppt_percent to only work on a specified
   * percent of the ppts.
   */
  public static String ppt_max_name = null;

  // The invariants detected will be serialized and written to this
  // file.
  public static File inv_file;

  // Whether we want the memory monitor activated
  private static boolean use_mem_monitor = false;

  /**
   * Whether Daikon should print its version number and date.
   **/
  public static boolean noversion_output = false;

  /**
   * Whether Daikon is in its inferencing loop.  Used only for
   * assertion checks.
   **/
  public static boolean isInferencing = false;

  /**
   * When true, omit certain invariants from the output .inv
   * file. Generally these are invariants that wouldn't be printed in
   * any case; but by default, they're retained in the .inv file in
   * case they would be useful for later processing. (For instance, we
   * might at some point in the future support resuming processing
   * with more data from an .inv file). These invariants can increase
   * the size of the .inv file, though, so when only limited further
   * processing is needed, it can save space to omit them.
   **/
  public static boolean omit_from_output = false;

  /**
   * An array of flags, indexed by characters, in which a true entry
   * means that invariants of that sort should be omitted from the
   * output .inv file.
   **/
  public static boolean[] omit_types = new boolean[256];

  // Public so other programs can reuse the same command-line options
  public static final String help_SWITCH = "help";
  public static final String ppt_regexp_SWITCH = "ppt";
  public static final String ppt_omit_regexp_SWITCH = "ppt_omit";
  public static final String list_type_SWITCH = "list_type";
  public static final String var_omit_regexp_SWITCH = "var_omit";
  public static final String no_text_output_SWITCH = "no_text_output";
  public static final String show_progress_SWITCH = "show_progress";
  public static final String no_show_progress_SWITCH = "no_show_progress";
  public static final String no_dataflow_hierarchy_SWITCH = "nohierarchy";
  public static final String suppress_redundant_SWITCH = "suppress_redundant";
  public static final String conf_limit_SWITCH = "conf_limit";
  public static final String esc_output_SWITCH = "esc_output";
  public static final String ioa_output_SWITCH = "ioa_output";
  public static final String test_ioa_output_SWITCH = "test_ioa_output";
  public static final String java_output_SWITCH = "java_output";
  public static final String jml_output_SWITCH = "jml_output";
  public static final String dbc_output_SWITCH = "dbc_output";
  public static final String mem_stat_SWITCH = "mem_stat";
  public static final String simplify_output_SWITCH = "simplify_output";
  public static final String output_num_samples_SWITCH = "output_num_samples";
  public static final String config_SWITCH = "config";
  public static final String config_option_SWITCH = "config_option";
  public static final String debugAll_SWITCH = "debug";
  public static final String debug_SWITCH = "dbg";
  public static final String files_from_SWITCH = "files_from";
  public static final String noversion_SWITCH = "noversion";
  public static final String disc_reason_SWITCH = "disc_reason";
  public static final String track_SWITCH = "track";
  public static final String omit_from_output_SWITCH = "omit_from_output";

  // A pptMap which contains all the Program Points
  public static PptMap all_ppts;

  /** current invariant (used for debugging) **/
  public static Invariant current_inv = null;

  /* List of prototype invariants (one for each type of invariant) */
  public static List /*Invariant*/ proto_invs = new ArrayList();

  /** Debug tracer. **/
  public static final Logger debugTrace = Logger.getLogger("daikon.Daikon");

  public static final Logger debugProgress = Logger.getLogger("daikon.Progress");

  public static final Logger debugEquality = Logger.getLogger("daikon.Equality");

  /** Prints out statistics concerning equality sets, suppressions, etc. **/
  public static final Logger debugStats = Logger.getLogger("daikon.stats");

  // Avoid problems if daikon.Runtime is loaded at analysis (rather than
  // test-run) time.  This might have to change when JTrace is used.
  static { daikon.Runtime.no_dtrace = true; }

  private static Stopwatch stopwatch = new Stopwatch();

  static String usage =
    UtilMDE.join(new String[] {
      release_string,
      "Daikon invariant detector, copyright 1998-2004",
      // " by Michael Ernst <mernst@csail.mit.edu>",
      "Uses the Java port of GNU getopt, copyright (c) 1998 Aaron M. Renn",
      "For licensing information, see the License section of the manual.",
      "Usage:",
      "    java daikon.Daikon [flags...] files...",
      "  Each file is a declaration file or a data trace file; the file type",
      "  is determined by the file name (containing \".decls\" or \".dtrace\").",
      "  For a list of flags, see the Daikon manual, which appears in the ",
      "  Daikon distribution and also at http://pag.csail.mit.edu/daikon/."},
                 lineSep);

  /**
   * The arguments to daikon.Daikon are file names.  Declaration file names
   * end in ".decls", and data trace file names end in ".dtrace".
   **/
  public static void main(final String[] args) {
    // Read command line options
    Set[] files = read_options(args);
    Assert.assertTrue(files.length == 4);
    Set decls_files = files[0];  // [File]
    Set dtrace_files = files[1]; // [File]
    Set spinfo_files = files[2]; // [File]
    Set map_files = files[3];    // [File]
    if ((decls_files.size() == 0) && (dtrace_files.size() == 0)) {
      System.out.println("No .decls or .dtrace files specified");
      System.exit(1);
    }

    // Set up debug traces; note this comes after reading command line options.
    LogHelper.setupLogs(Global.debugAll ? LogHelper.FINE : LogHelper.INFO);

    if (! noversion_output) {
      System.out.println(release_string);
    }

    // Create the list of all invariant types
    setup_proto_invs();

    if (PrintInvariants.print_discarded_invariants) {
      DiscReasonMap.initialize();
    }

    fileio_progress.start();

    // Load declarations and splitters
    all_ppts = load_decls_files(decls_files);
    load_spinfo_files(all_ppts, spinfo_files);
    load_map_files(all_ppts, map_files);

    Dataflow.init_partial_order(all_ppts);
    if (debugTrace.isLoggable(Level.FINE)) {
      debugTrace.fine ("Partial order initialized");
    }
    PptTopLevel.init (all_ppts);
    all_ppts.trimToSize();


    // If requested, just calculate the total number of invariants possible
    if (dkconfig_calc_possible_invs) {
      fileio_progress.shouldStop = true;
      setupEquality (all_ppts);
      int total_invs = 0;
      for (Iterator itor = all_ppts.ppt_all_iterator() ; itor.hasNext() ; ) {
        PptTopLevel ppt = (PptTopLevel) itor.next();
        System.out.println ("Processing " + ppt.name() + " with "
                            + ppt.var_infos.length + " variables");
        int inv_cnt = 0;
        if (ppt.var_infos.length > 1600) {
          System.out.println ("Skipping, too many variables!");
        } else {
          ppt.instantiate_views_and_invariants();
          inv_cnt = ppt.invariant_cnt();
          ppt.clean_for_merge();
          System.out.println (inv_cnt + " invariants in " + ppt.name());
          total_invs += inv_cnt;
        }
      }
      System.out.println (total_invs + "invariants total");
      System.exit(0);
    }

    // Only for assertion checks
    isInferencing = true;

    // Infer invariants
    process_data(all_ppts, dtrace_files);
    isInferencing = false;
    if (Debug.logOn())
      Debug.check (all_ppts, "After process data");

    if (suppress_redundant_invariants_with_simplify) {
      suppressWithSimplify(all_ppts);
    }

    // Check that PptMap created was correct
    all_ppts.repCheck();

    // Remove undesired invariants, if requested
    if (omit_from_output) {
      processOmissions(all_ppts);
    }

    // Write serialized output - must be done before guarding invariants
    if (inv_file != null) {
      try {
        FileIO.write_serialized_pptmap(all_ppts, inv_file);
      } catch (IOException e) {
        throw new RuntimeException("Error while writing .inv file "
                                   + "'" + inv_file + "': " + e.toString());
      }
    }

    // Guard invariants
    if ((Daikon.output_style == OutputFormat.JML
         || Daikon.output_style == OutputFormat.ESCJAVA)
        && !dkconfig_noInvariantGuarding)
      guardInvariants(all_ppts);

    // Display invariants
    if (output_num_samples) {
      System.out.println("The --output_num_samples debugging flag is on.");
      System.out.println("Some of the debugging output may only make sense to Daikon programmers.");
    }

    // If they want to see discarded invariants, they probably don't
    // want to see the true ones.
    if (!PrintInvariants.print_discarded_invariants) {
      PrintInvariants.print_invariants(all_ppts);
    } else {
      PrintInvariants.print_reasons(all_ppts);
    }

    if (output_num_samples) {
      Global.output_statistics();
    }
    if (dkconfig_print_sample_totals)
      System.out.println (FileIO.samples_processed + " samples processed of "
                          + FileIO.samples_considered + " total samples");

    // print statistics concerning what invariants are printed
    if (debugStats.isLoggable (Level.FINE)) {
      for (Iterator itor = all_ppts.ppt_all_iterator() ; itor.hasNext() ; ) {
        PptTopLevel ppt = (PptTopLevel) itor.next();
        PrintInvariants.print_filter_stats (debugStats, ppt, all_ppts);
      }
    }

    // Done
    System.out.println("Exiting");
  }

  ///////////////////////////////////////////////////////////////////////////
  // Read in the command line options
  // Return an array of {decls, dtrace, spinfo, map} files; each array
  // element is a set.
  private static Set[] read_options(String[] args) {
    if (args.length == 0) {
      System.out.println("Daikon error: no files supplied on command line.");
      System.out.println(usage);
      System.exit(1);
    }

    Set decl_files = new HashSet();
    Set dtrace_files = new HashSet();
    Set spinfo_files = new HashSet();
    Set map_files = new HashSet();

    LongOpt[] longopts = new LongOpt[] {
      new LongOpt(help_SWITCH, LongOpt.NO_ARGUMENT, null, 0),
      new LongOpt(ppt_regexp_SWITCH, LongOpt.REQUIRED_ARGUMENT, null, 0),
      new LongOpt(ppt_omit_regexp_SWITCH, LongOpt.REQUIRED_ARGUMENT, null, 0),
      new LongOpt(list_type_SWITCH, LongOpt.REQUIRED_ARGUMENT, null, 0),
      new LongOpt(var_omit_regexp_SWITCH, LongOpt.REQUIRED_ARGUMENT, null, 0),
      new LongOpt(no_text_output_SWITCH, LongOpt.NO_ARGUMENT, null, 0),
      new LongOpt(show_progress_SWITCH, LongOpt.NO_ARGUMENT, null, 0),
      new LongOpt(no_show_progress_SWITCH, LongOpt.NO_ARGUMENT, null, 0),
      new LongOpt(no_dataflow_hierarchy_SWITCH, LongOpt.NO_ARGUMENT, null, 0),
      new LongOpt(suppress_redundant_SWITCH, LongOpt.NO_ARGUMENT, null, 0),
      new LongOpt(conf_limit_SWITCH, LongOpt.REQUIRED_ARGUMENT, null, 0),
      new LongOpt(esc_output_SWITCH, LongOpt.NO_ARGUMENT, null, 0),
      new LongOpt(simplify_output_SWITCH, LongOpt.NO_ARGUMENT, null, 0),
      new LongOpt(dbc_output_SWITCH, LongOpt.NO_ARGUMENT, null, 0),
      new LongOpt(ioa_output_SWITCH, LongOpt.NO_ARGUMENT, null, 0),
      new LongOpt(test_ioa_output_SWITCH, LongOpt.NO_ARGUMENT, null, 0),
      new LongOpt(java_output_SWITCH, LongOpt.NO_ARGUMENT, null, 0),
      new LongOpt(jml_output_SWITCH, LongOpt.NO_ARGUMENT, null, 0),
      new LongOpt(mem_stat_SWITCH, LongOpt.NO_ARGUMENT, null, 0),
      new LongOpt(output_num_samples_SWITCH, LongOpt.NO_ARGUMENT, null, 0),
      new LongOpt(config_SWITCH, LongOpt.REQUIRED_ARGUMENT, null, 0),
      new LongOpt(config_option_SWITCH, LongOpt.REQUIRED_ARGUMENT, null, 0),
      new LongOpt(debugAll_SWITCH, LongOpt.NO_ARGUMENT, null, 0),
      new LongOpt(debug_SWITCH, LongOpt.REQUIRED_ARGUMENT, null, 0),
      new LongOpt(files_from_SWITCH, LongOpt.REQUIRED_ARGUMENT, null, 0),
      new LongOpt(noversion_SWITCH, LongOpt.NO_ARGUMENT, null, 0),
      new LongOpt(disc_reason_SWITCH, LongOpt.REQUIRED_ARGUMENT, null, 0),
      new LongOpt(track_SWITCH, LongOpt.REQUIRED_ARGUMENT, null, 0),
      new LongOpt(omit_from_output_SWITCH, LongOpt.REQUIRED_ARGUMENT, null, 0),
    };
    Getopt g = new Getopt("daikon.Daikon", args, "ho:", longopts);
    int c;

    while ((c = g.getopt()) != -1) {
      switch(c) {
      case 0:
        // got a long option
        String option_name = longopts[g.getLongind()].getName();
        if (help_SWITCH.equals(option_name)) {
          System.out.println(usage);
          System.exit(1);
        } else if (disc_reason_SWITCH.equals(option_name)) {
          try { PrintInvariants.discReasonSetup(g.getOptarg()); }
          catch (IllegalArgumentException e) {
            System.out.print(e.getMessage());
            System.exit(1);
          }
        } else if (track_SWITCH.equals (option_name)) {
          LogHelper.setLevel("daikon.Debug", LogHelper.FINE);
          String error = Debug.add_track (g.getOptarg());
          if (error != null) {
            System.out.println ("Error parsing track argument '"
                                + g.getOptarg() + "' - " + error);
            System.exit(1);
          }
        } else if (ppt_regexp_SWITCH.equals(option_name)) {
          if (ppt_regexp != null)
            throw new Error("multiple --" + ppt_regexp_SWITCH
                            + " regular expressions supplied on command line");
          try {
            String regexp_string = g.getOptarg();
            // System.out.println("Regexp = " + regexp_string);
            ppt_regexp = Global.regexp_compiler.compile(regexp_string);
          } catch (Exception e) {
            throw new Error(e.toString());
          }
          break;
        } else if (ppt_omit_regexp_SWITCH.equals(option_name)) {
          if (ppt_omit_regexp != null)
            throw new Error("multiple --" + ppt_omit_regexp_SWITCH
                            + " regular expressions supplied on command line");
          try {
            String regexp_string = g.getOptarg();
            // System.out.println("Regexp = " + regexp_string);
            ppt_omit_regexp = Global.regexp_compiler.compile(regexp_string);
          } catch (Exception e) {
            throw new Error(e.toString());
          }
          break;
        } else if (list_type_SWITCH.equals(option_name)) {
          try {
            String list_type_string = g.getOptarg();
            ProglangType.list_implementors.add(list_type_string);
          } catch (Exception e) {
            throw new Error(e.toString());
          }
          break;
        } else if (var_omit_regexp_SWITCH.equals(option_name)) {
          if (var_omit_regexp != null)
            throw new Error("multiple --" + var_omit_regexp_SWITCH
                            + " regular expressions supplied on command line");
          try {
            String regexp_string = g.getOptarg();
            // System.out.println("Regexp = " + regexp_string);
            var_omit_regexp = Global.regexp_compiler.compile(regexp_string);
          } catch (Exception e) {
            throw new Error(e.toString());
          }
          break;
        } else if (debugAll_SWITCH.equals(option_name)) {
          Global.debugAll = true;
        } else if (debug_SWITCH.equals(option_name)) {
          LogHelper.setLevel(g.getOptarg(), LogHelper.FINE);
        } else if (no_text_output_SWITCH.equals(option_name)) {
          no_text_output = true;
        } else if (show_progress_SWITCH.equals(option_name)) {
          show_progress = true;
          LogHelper.setLevel ("daikon.Progress", LogHelper.FINE);
        } else if (no_show_progress_SWITCH.equals(option_name)) {
          show_progress = false;
        } else if (no_dataflow_hierarchy_SWITCH.equals(option_name)) {
          use_dataflow_hierarchy = false;
        } else if (suppress_redundant_SWITCH.equals(option_name)) {
          suppress_redundant_invariants_with_simplify = true;
        } else if (conf_limit_SWITCH.equals(option_name)) {
          double limit = Double.parseDouble(g.getOptarg());
          if ((limit < 0.0) || (limit > 1.0)) {
            throw new Error(conf_limit_SWITCH + " must be between [0..1]");
          }
          Configuration.getInstance().apply(
            "daikon.inv.Invariant.confidence_limit", String.valueOf(limit));
        } else if (esc_output_SWITCH.equals(option_name)) {
          output_style = OutputFormat.ESCJAVA;
        } else if (simplify_output_SWITCH.equals(option_name)) {
          output_style = OutputFormat.SIMPLIFY;
        } else if (ioa_output_SWITCH.equals(option_name)) {
          output_style = OutputFormat.IOA;
        } else if (test_ioa_output_SWITCH.equals(option_name)) {
          output_style = OutputFormat.IOA;
          PrintInvariants.test_output = true;
        } else if (java_output_SWITCH.equals(option_name)) {
          output_style = OutputFormat.JAVA;
        } else if (jml_output_SWITCH.equals(option_name)) {
          output_style = OutputFormat.JML;
        } else if (dbc_output_SWITCH.equals(option_name)) {
          output_style = OutputFormat.DBCJAVA;
        } else if (mem_stat_SWITCH.equals(option_name)) {
          use_mem_monitor = true;
        } else if (output_num_samples_SWITCH.equals(option_name)) {
          output_num_samples = true;
        } else if (config_SWITCH.equals(option_name)) {
          String config_file = g.getOptarg();
          try {
            InputStream stream = new FileInputStream(config_file);
            Configuration.getInstance().apply(stream);
          } catch (IOException e) {
            throw new RuntimeException("Could not open config file " + config_file);
          }
          break;
        } else if (config_option_SWITCH.equals(option_name)) {
          String item = g.getOptarg();
          Configuration.getInstance().apply(item);
          break;
        } else if (files_from_SWITCH.equals(option_name)) {
          try {
            BufferedReader files_from = UtilMDE.BufferedFileReader(g.getOptarg());
            String filename;
            while ((filename = files_from.readLine()) != null) {
              // Ignore blank lines in file.
              if (filename.equals("")) {
                continue;
              }
              // This code is duplicated below outside the options loop.
              // These aren't "endsWith()" because there might be a suffix
              // on the end (eg, a date).
              File file = new File(filename);
              if (! file.exists()) {
                throw new Error("File " + filename + " not found.");
              }
              if (filename.indexOf(".decls") != -1) {
                decl_files.add(file);
              } else if (filename.indexOf(".dtrace") != -1) {
                dtrace_files.add(file);
              } else if (filename.indexOf(".spinfo") != -1) {
                spinfo_files.add(file);
              } else if (filename.indexOf(".map") != -1) {
                map_files.add(file);
              } else {
                throw new Error("Unrecognized file extension: " + filename);
              }
            }
          } catch (IOException e) {
            throw new RuntimeException("Error reading --files_from file");
          }
          break;
        } else if (noversion_SWITCH.equals(option_name)) {
          noversion_output = true;
        } else if (omit_from_output_SWITCH.equals(option_name)) {
          String f = g.getOptarg();
          for (int i = 0; i < f.length(); i++) {
            if ("0rs".indexOf(f.charAt(i)) == -1)
              throw new RuntimeException("omit_from_output flag letter '"
                                         + f.charAt(i) + "' is unknown");
            omit_types[f.charAt(i)] = true;
          }
          omit_from_output = true;
        } else {
          throw new RuntimeException("Unknown long option received: " + option_name);
        }
        break;
      case 'h':
        System.out.println(usage);
        System.exit(1);
        break;
      case 'o':
        if (inv_file != null)
          throw new Error("multiple serialization output files supplied on command line");

        String inv_filename = g.getOptarg();
        inv_file = new File(inv_filename);

        if (! UtilMDE.canCreateAndWrite(inv_file)) {
          throw new Error("Cannot write to file " + inv_file);
        }
        break;
        //
      case '?':
        break; // getopt() already printed an error
        //
      default:
        System.out.print("getopt() returned " + c + lineSep);
        break;
      }
    }

    // This code is duplicated above within the switch processing
    // First check that all the file names are OK, so we don't do lots of
    // processing only to bail out at the end.
    for (int i=g.getOptind(); i<args.length; i++) {
      File file = new File(args[i]);
      // These aren't "endsWith()" because there might be a suffix on the end
      // (eg, a date).
      if (! file.exists()) {
        throw new Error("File " + file + " not found.");
      }
      String filename = file.toString();
      if (filename.indexOf(".decls") != -1) {
        decl_files.add(file);
      } else if (filename.indexOf(".dtrace") != -1) {
        dtrace_files.add(file);
      } else if (filename.indexOf(".spinfo") != -1) {
        spinfo_files.add(file);
      } else if (filename.indexOf(".map") != -1) {
        map_files.add(file);
      } else {
        throw new Error("Unrecognized argument: " + file);
      }
    }

    // Set the fuzzy float comparison ratio.  This needs to be done after
    // any configuration options (which may set the ratio) are processed.
    Global.fuzzy.set_rel_diff (Invariant.dkconfig_fuzzy_ratio);

    // Enable dynamic constants for bottom up only
    if (!dkconfig_df_bottom_up)
      dkconfig_use_dynamic_constant_optimization = false;

    // Setup ppt_max_name based on the specified percentage of ppts to process
    if (dkconfig_ppt_perc != 100) {
      ppt_max_name = setup_ppt_perc (decl_files, dkconfig_ppt_perc);
      System.out.println ("Max ppt name = " + ppt_max_name);
    }

    return new Set[] {
      decl_files,
      dtrace_files,
      spinfo_files,
      map_files,
    };
  }


  /**
   * Creates the list of prototype invariants for all Daikon invariants.
   * New invariants must be added to this list
   */
  public static void setup_proto_invs() {

    // Unary scalar invariants
    {
      // OneOf (OneOf.java.jpp)
      proto_invs.add (OneOfScalar.get_proto());
      proto_invs.add (OneOfFloat.get_proto());
      proto_invs.add (OneOfString.get_proto());

      // NonZero (NonZero.java.jpp)
      proto_invs.add (NonZero.get_proto());
      proto_invs.add (NonZeroFloat.get_proto());

      // Lower and Upper bound (Bound.java.jpp)
      proto_invs.add (LowerBound.get_proto());
      proto_invs.add (LowerBoundFloat.get_proto());
      proto_invs.add (UpperBound.get_proto());
      proto_invs.add (UpperBoundFloat.get_proto());

      // Modulus and NonModulus (Modulus.java and NonModulus.java)
      proto_invs.add (Modulus.get_proto());
      proto_invs.add (NonModulus.get_proto());

      // Range invariant (Range.java.jpp)
      proto_invs.addAll (RangeInt.get_proto_all());
      proto_invs.addAll (RangeFloat.get_proto_all());

      // Positive (x > 0) (Postive.java).  Positive is a sample invariant
      // that is only included as an example.
      // proto_invs.add (Postive.get_proto());
    }

    // Unary sequence invariants
    {
      // OneOf (OneOf.java.jpp)
      proto_invs.add (OneOfSequence.get_proto());
      proto_invs.add (OneOfFloatSequence.get_proto());
      proto_invs.add (OneOfStringSequence.get_proto());
      proto_invs.add (EltOneOf.get_proto());
      proto_invs.add (EltOneOfFloat.get_proto());
      proto_invs.add (EltOneOfString.get_proto());

      // Sequence Index Comparisons (SeqIndexComparison.java.jpp)
      proto_invs.add (SeqIndexIntEqual.get_proto());
      proto_invs.add (SeqIndexIntNonEqual.get_proto());
      proto_invs.add (SeqIndexIntGreaterEqual.get_proto());
      proto_invs.add (SeqIndexIntGreaterThan.get_proto());
      proto_invs.add (SeqIndexIntLessEqual.get_proto());
      proto_invs.add (SeqIndexIntLessThan.get_proto());
      proto_invs.add (SeqIndexFloatEqual.get_proto());
      proto_invs.add (SeqIndexFloatNonEqual.get_proto());
      proto_invs.add (SeqIndexFloatGreaterEqual.get_proto());
      proto_invs.add (SeqIndexFloatGreaterThan.get_proto());
      proto_invs.add (SeqIndexFloatLessEqual.get_proto());
      proto_invs.add (SeqIndexFloatLessThan.get_proto());

      // foreach i compare a[i] to a[i+1] (EltwiseIntComparisons.java.jpp)
      proto_invs.add (EltwiseIntEqual.get_proto());
      proto_invs.add (EltwiseIntLessEqual.get_proto());
      proto_invs.add (EltwiseIntGreaterEqual.get_proto());
      proto_invs.add (EltwiseIntLessThan.get_proto());
      proto_invs.add (EltwiseIntGreaterThan.get_proto());
      proto_invs.add (EltwiseFloatEqual.get_proto());
      proto_invs.add (EltwiseFloatLessEqual.get_proto());
      proto_invs.add (EltwiseFloatGreaterEqual.get_proto());
      proto_invs.add (EltwiseFloatLessThan.get_proto());
      proto_invs.add (EltwiseFloatGreaterThan.get_proto());

      // EltNonZero (EltNonZero.java.jpp)
      proto_invs.add (EltNonZero.get_proto());
      proto_invs.add (EltNonZeroFloat.get_proto());

      // No Duplicates (NoDuplicates.java.jpp)
      proto_invs.add (NoDuplicates.get_proto());
      proto_invs.add (NoDuplicatesFloat.get_proto());

      // Element bounds (Bound.java.jpp)
      proto_invs.add (EltLowerBound.get_proto());
      proto_invs.add (EltUpperBound.get_proto());
      proto_invs.add (EltLowerBoundFloat.get_proto());
      proto_invs.add (EltUpperBoundFloat.get_proto());
    }

    // Binary scalar-scalar invariants
    {
      // Int, Float, String comparisons (from IntComparisons.java.jpp)
      proto_invs.add (IntEqual.get_proto());
      proto_invs.add (IntNonEqual.get_proto());
      proto_invs.add (IntLessThan.get_proto());
      proto_invs.add (IntGreaterThan.get_proto());
      proto_invs.add (IntLessEqual.get_proto());
      proto_invs.add (IntGreaterEqual.get_proto());
      proto_invs.add (FloatEqual.get_proto());
      proto_invs.add (FloatNonEqual.get_proto());
      proto_invs.add (FloatLessThan.get_proto());
      proto_invs.add (FloatGreaterThan.get_proto());
      proto_invs.add (FloatLessEqual.get_proto());
      proto_invs.add (FloatGreaterEqual.get_proto());
      proto_invs.add (StringEqual.get_proto());
      proto_invs.add (StringNonEqual.get_proto());
      proto_invs.add (StringLessThan.get_proto());
      proto_invs.add (StringGreaterThan.get_proto());
      proto_invs.add (StringLessEqual.get_proto());
      proto_invs.add (StringGreaterEqual.get_proto());

      // LinearBinary over integer/float (from LinearBinary.java.jpp)
      proto_invs.add (LinearBinary.get_proto());
      proto_invs.add (LinearBinaryFloat.get_proto());

      // Numeric invariants (from Numeric.java.jpp)
      proto_invs.addAll (NumericInt.get_proto_all());
      proto_invs.addAll (NumericFloat.get_proto_all());
    }

    // Binary sequence-sequence invariants
    {
      // Numeric invariants (from Numeric.java.jpp)
      proto_invs.addAll (PairwiseNumericInt.get_proto_all());
      proto_invs.addAll (PairwiseNumericFloat.get_proto_all());

      // Lexical sequence comparisons (from SeqComparison.java.jpp)
      proto_invs.add (SeqSeqIntEqual.get_proto());
      proto_invs.add (SeqSeqIntLessThan.get_proto());
      proto_invs.add (SeqSeqIntGreaterThan.get_proto());
      proto_invs.add (SeqSeqIntLessEqual.get_proto());
      proto_invs.add (SeqSeqIntGreaterEqual.get_proto());
      proto_invs.add (SeqSeqFloatEqual.get_proto());
      proto_invs.add (SeqSeqFloatLessThan.get_proto());
      proto_invs.add (SeqSeqFloatGreaterThan.get_proto());
      proto_invs.add (SeqSeqFloatLessEqual.get_proto());
      proto_invs.add (SeqSeqFloatGreaterEqual.get_proto());
      proto_invs.add (SeqSeqStringEqual.get_proto());
      proto_invs.add (SeqSeqStringLessThan.get_proto());
      proto_invs.add (SeqSeqStringGreaterThan.get_proto());
      proto_invs.add (SeqSeqStringLessEqual.get_proto());
      proto_invs.add (SeqSeqStringGreaterEqual.get_proto());

      // Pairwise sequence comparisons (from PairwiseIntComparison.java.jpp)
      proto_invs.add (PairwiseIntEqual.get_proto());
      proto_invs.add (PairwiseIntLessThan.get_proto());
      proto_invs.add (PairwiseIntGreaterThan.get_proto());
      proto_invs.add (PairwiseIntLessEqual.get_proto());
      proto_invs.add (PairwiseIntGreaterEqual.get_proto());
      proto_invs.add (PairwiseFloatEqual.get_proto());
      proto_invs.add (PairwiseFloatLessThan.get_proto());
      proto_invs.add (PairwiseFloatGreaterThan.get_proto());
      proto_invs.add (PairwiseFloatLessEqual.get_proto());
      proto_invs.add (PairwiseFloatGreaterEqual.get_proto());

      // Array Reverse (from Reverse.java.jpp)
      proto_invs.add (Reverse.get_proto());
      proto_invs.add (ReverseFloat.get_proto());

      // Pairwise Linear Binary (from PairwiseLinearBinary.java.jpp)
      proto_invs.add (PairwiseLinearBinary.get_proto());
      proto_invs.add (PairwiseLinearBinaryFloat.get_proto());

      // Subset and Superset (from SubSet.java.jpp)
      proto_invs.add (SubSet.get_proto());
      proto_invs.add (SuperSet.get_proto());
      proto_invs.add (SubSetFloat.get_proto());
      proto_invs.add (SuperSetFloat.get_proto());

      // Subsequence (from SubSequence.java.jpp)
      proto_invs.add (SubSequence.get_proto());
      proto_invs.add (SubSequenceFloat.get_proto());
      proto_invs.add (SuperSequence.get_proto());
      proto_invs.add (SuperSequenceFloat.get_proto());
    }

    // Binary sequence-scalar invariants
    {
      // Comparison of scalar to each array element (SeqIntComparison.java.jpp)
      proto_invs.add (SeqIntEqual.get_proto());
      proto_invs.add (SeqIntLessThan.get_proto());
      proto_invs.add (SeqIntGreaterThan.get_proto());
      proto_invs.add (SeqIntLessEqual.get_proto());
      proto_invs.add (SeqIntGreaterEqual.get_proto());
      proto_invs.add (SeqFloatEqual.get_proto());
      proto_invs.add (SeqFloatLessThan.get_proto());
      proto_invs.add (SeqFloatGreaterThan.get_proto());
      proto_invs.add (SeqFloatLessEqual.get_proto());
      proto_invs.add (SeqFloatGreaterEqual.get_proto());

      // Scalar is an element of the array (Member.java.jpp)
      proto_invs.add (Member.get_proto());
      proto_invs.add (MemberFloat.get_proto());
      proto_invs.add (MemberString.get_proto());
    }

    // Ternary invariants
    {
      // FunctionBinary (FunctionBinary.java.jpp)
      proto_invs.addAll (FunctionBinary.get_proto_all());
      proto_invs.addAll (FunctionBinaryFloat.get_proto_all());

      // LinearTernary (LinearTernary.java.jpp)
      proto_invs.add (LinearTernary.get_proto());
      proto_invs.add (LinearTernaryFloat.get_proto());
    }

    // Remove any null elements (can happen if an invariant is not enabled)
    for (Iterator i = proto_invs.iterator(); i.hasNext(); ) {
      Invariant inv = (Invariant) i.next();
      if (inv == null)
        i.remove();
    }
  }


  ///////////////////////////////////////////////////////////////////////////
  // Read decls, dtrace, etc. files

  private static PptMap load_decls_files(Set decl_files) {
    stopwatch.reset();
    try {
      System.out.print("Reading declaration files ");
      PptMap all_ppts = FileIO.read_declaration_files(decl_files);
      if (debugTrace.isLoggable(Level.FINE)) {
        debugTrace.fine ("Initializing partial order");
      }
      fileio_progress.clear();
      System.out.print(" (read ");
      System.out.print(UtilMDE.nplural(decl_files.size(), "decls file"));
      System.out.println(")");
      return all_ppts;
    } catch (IOException e) {
      System.out.println();
      e.printStackTrace();
      throw new Error(e.toString());
    } finally {
      debugProgress.fine ("Time spent on read_declaration_files: " + stopwatch.format());
    }
  }

  private static void load_spinfo_files(PptMap all_ppts,
                                        Set spinfo_files // [File]
                                        ) {
    stopwatch.reset();
    if (!dkconfig_disable_splitting && spinfo_files.size() > 0) {
      try {
        System.out.print("Reading splitter info files ");
        create_splitters(all_ppts, spinfo_files);
        System.out.print(" (read ");
        System.out.print(UtilMDE.nplural(spinfo_files.size(), "spinfo file"));
        System.out.println(")");
      } catch (IOException e) {
        System.out.println();
        e.printStackTrace();
        throw new Error(e.toString());
      } finally {
        debugProgress.fine ("Time spent on load_spinfo_files: " + stopwatch.format());
      }
    }
  }

  private static void load_map_files(PptMap all_ppts,
                                     Set map_files // [File]
                                     ) {
    stopwatch.reset();
    if (!dkconfig_disable_splitting && map_files.size() > 0) {
      System.out.print("Reading map (context) files ");
      ContextSplitterFactory.load_mapfiles_into_splitterlist(
        map_files, ContextSplitterFactory.dkconfig_granularity);
      System.out.print(" (read ");
      System.out.print(UtilMDE.nplural(map_files.size(), "map (context) file"));
      System.out.println(")");
      debugProgress.fine ("Time spent on load_map_files: " + stopwatch.format());
    }
  }

  public static void setup_splitters(PptMap all_ppts) {
    if (dkconfig_disable_splitting) {
      return;
    }

    for (Iterator itor = all_ppts.pptIterator() ; itor.hasNext() ; ) {
      PptTopLevel ppt = (PptTopLevel) itor.next();

      Splitter[] pconds = null;
      if (SplitterList.dkconfig_all_splitters) {
        pconds = SplitterList.get_all();
      } else {
        pconds = SplitterList.get(ppt.name());
      }
      if (pconds != null) {
        if (Global.debugSplit.isLoggable(Level.FINE)) {
          Global.debugSplit.fine ("Got "
                                  + UtilMDE.nplural(pconds.length, "splitter")
                                  + " for " + ppt.name());
        }
        ppt.addConditions(pconds);
      }
    }
  }

  ///////////////////////////////////////////////////////////////////////////
  // Infer invariants over the trace data

  /**
   * The amount of time to wait between updates of the progress
   * display, measured in milliseconds. A value of -1 means not to
   * print the progress display at all.
   **/
  public static int dkconfig_progress_delay = 1000;

  /**
   * The number of columns of progress information to display. In many
   * Unix shells, this can be set to an appropriate value by
   * --config_option daikon.Daikon.progress_display_width=$COLUMNS
   * .
   **/
  public static int dkconfig_progress_display_width = 80;

  /** A way to output FileIO progress information easily. */
  private static final FileIOProgress fileio_progress = new FileIOProgress();
  public static class FileIOProgress extends Thread {
    public FileIOProgress() {
      setDaemon(true);
      pctFmt = NumberFormat.getPercentInstance();
      pctFmt.setMinimumFractionDigits(2);
      pctFmt.setMaximumFractionDigits(2);
      df = DateFormat.getTimeInstance(/*DateFormat.LONG*/);
    }
    /**
     * Clients should set this variable instead of calling Thread.stop(),
     * which is deprecated.  Typically a client calls "display()" before
     * setting this.
     **/
    public boolean shouldStop = false;
    private static NumberFormat pctFmt;
    private DateFormat df;
    public void run() {
      if (dkconfig_progress_delay == -1)
        return;
      while (true) {
        if (shouldStop) {
          clear();
          return;
        }
        display();
        try {
          sleep(dkconfig_progress_delay);
        } catch (InterruptedException e) {
          // hmm
        }
      }
    }
    /** Clear the display; good to do before printing to System.out. **/
    public void clear() {
      if (dkconfig_progress_delay == -1)
        return;
      // "display("");" is wrong becuase it leaves the timestamp and writes
      // spaces across the screen.
      String status = UtilMDE.rpad("", dkconfig_progress_display_width - 1);
      System.out.print("\r" + status.toString());
      System.out.print("\r");   // return to beginning of line
      System.out.flush();
    }
    /**
     * Displays the current status.
     * Call this if you don't want to wait until the next automatic display.
     **/
    public void display() {
      if (dkconfig_progress_delay == -1)
        return;
      display(message());
    }
    /** Displays the given message. **/
    public void display(String message) {
      if (dkconfig_progress_delay == -1)
        return;
      String status
        = UtilMDE.rpad("[" + df.format(new Date()) + "]: " + message,
                       dkconfig_progress_display_width - 1);
      System.out.print("\r" + status);
      System.out.flush();
      // System.out.println (status);

      if (debugTrace.isLoggable(Level.FINE)) {
        debugTrace.fine ("Free memory: " + java.lang.Runtime.getRuntime().freeMemory());
        debugTrace.fine ("Used memory: " +
                         (java.lang.Runtime.getRuntime().totalMemory()
                          - java.lang.Runtime.getRuntime().freeMemory()));
        debugTrace.fine ("Active slices: " + FileIO.data_num_slices);
      }
    }
    private String message() {
      File file = FileIO.data_trace_filename;
      if (file == null) {
        if (Dataflow.progress == null) {
          return "[no status]";
        } else {
          return Dataflow.progress;
        }
      }
      LineNumberReader lnr = FileIO.data_trace_reader;
      String line;
      if (lnr == null) {
        line = "?";
      } else {
        long lineNum = lnr.getLineNumber();
        line = String.valueOf(lineNum);
        if (FileIO.data_trace_total_lines > 0) {
          double frac = lineNum / (double)FileIO.data_trace_total_lines;
          String percent = pctFmt.format(frac);
          line = line + ", " + percent;
        }
      }
      return "Reading " + file.getName() + " (line " + line + ") ...";
    }
  }

  /**
   * The main data-processing routine of the daikon engine.  At this
   * point, the decls and spinfo files have been loaded, all of the
   * program points have been setup, and candidate invariants have
   * been instantiated.  This routine processes data to falsify the
   * candidate invariants.
   **/
  private static void process_data(PptMap all_ppts,
                                   Set dtrace_files) {
    MemMonitor monitor = null;
    if (use_mem_monitor) {
      monitor = new MemMonitor("stat.out");
      new Thread((Runnable) monitor).start();
    }

    stopwatch.reset();

    // Preprocessing
    setupEquality (all_ppts);
    setup_NISuppression();

    // Processing (actually using dtrace files)
    try {
      fileio_progress.clear();
      System.out.println("Processing trace data; reading "
                         + UtilMDE.nplural(dtrace_files.size(), "dtrace file")
                         + ":");
      FileIO.read_data_trace_files(dtrace_files, all_ppts);
      fileio_progress.shouldStop = true;
      // Final update, so "100%", not "99.70%", is the last thing printed.
      fileio_progress.display();
      System.out.println();
      // System.out.print("Creating implications "); // XXX untested code
      // for (Iterator itor = all_ppts.pptIterator() ; itor.hasNext() ; ) {
      //   PptTopLevel ppt = (PptTopLevel) itor.next();
      //   System.out.print('.');
      //   ppt.addImplications();
      // }
      // System.out.println();
    } catch (IOException e) {
      System.out.println();
      e.printStackTrace();
      throw new Error(e.toString());
    } finally {
      debugProgress.fine ("Time spent on read_data_trace_files: " + stopwatch.format());
    }

    if (monitor != null) {
      monitor.stop();
    }

    if (FileIO.dkconfig_read_samples_only) {
      Fmt.pf ("Finished reading %s samples", "" + FileIO.samples_processed);
      System.exit (0);
    }

//     if (debugStats.isLoggable (Level.FINE)) {
//       PptSliceEquality.print_equality_stats (debugStats, all_ppts);
//       if (false) {
//         for (Iterator i = all_ppts.pptIterator(); i.hasNext(); ) {
//           PptTopLevel ppt = (PptTopLevel) i.next();
//           if (ppt.name().indexOf ("EXIT42") >= 0) {
//             System.out.println (ppt.name() + " After processing data");
//             ppt.print_suppressed_invs (debugStats);
//           }
//         }
//       }
//     }

    // Print equality set info
//     for (Iterator i = all_ppts.pptIterator(); i.hasNext(); ) {
//       PptTopLevel ppt = (PptTopLevel) i.next();
//       Fmt.pf ("ppt: %s", ppt.name);
//       if ((ppt.equality_view == null) || (ppt.equality_view.invs == null))
//         continue;
//       for (int j = 0; j < ppt.equality_view.invs.size(); j++) {
//         Equality e = (Equality) ppt.equality_view.invs.get(j);
//         Fmt.pf ("    equality set = %s", e);
//       }
//     }

    // Fmt.pf ("printing ternary invariants");
    // PrintInvariants.print_all_ternary_invs (all_ppts);
    // System.exit(0);

    // Postprocessing

    stopwatch.reset();

    // Post process dynamic constants
    if (dkconfig_use_dynamic_constant_optimization) {
      for (Iterator itor = all_ppts.ppt_all_iterator() ; itor.hasNext() ; ) {
        PptTopLevel ppt = (PptTopLevel) itor.next();
        if (ppt.constants != null)
          ppt.constants.post_process();
      }
    }

    // If we are processing dataflow bottom up
    if (dkconfig_df_bottom_up) {

      // Initialize the partial order hierarchy
      debugProgress.fine ("Init Hierarchy ... ");
      PptRelation.init_hierarchy (all_ppts);
      debugProgress.fine ("Init Hierarchy ... done");

      // Calculate invariants at all non-leaf ppts
      if (use_dataflow_hierarchy) {
        debugProgress.fine ("createUpperPpts");
        Dataflow.createUpperPpts (all_ppts);
        debugProgress.fine ("createUpperPpts ... done");
      }
    }

    // Equality data for each PptTopLevel.
    if (Daikon.use_equality_optimization) {
      debugProgress.fine ("Equality Post Process ... ");
      for (Iterator itor = all_ppts.ppt_all_iterator() ; itor.hasNext() ; ) {
        PptTopLevel ppt = (PptTopLevel) itor.next();
        ppt.postProcessEquality();
      }
      debugProgress.fine ("Equality Post Process ... done");
    }

    if (debugEquality.isLoggable (Level.FINE)) {
      for (Iterator itor = all_ppts.ppt_all_iterator() ; itor.hasNext() ; ) {
        PptTopLevel ppt = (PptTopLevel) itor.next();
        debugEquality.fine (ppt.name() +": " + ppt.equality_sets_txt());
      }
    }

    // One more round of suppression for printing
    debugProgress.fine ("Suppress for printing ... ");
    for (Iterator itor = all_ppts.ppt_all_iterator() ; itor.hasNext() ; ) {
      PptTopLevel ppt = (PptTopLevel) itor.next();
      ppt.suppressAll (false);
    }
    debugProgress.fine ("Suppress for printing ... done");

    debugProgress.fine ("Time spent on non-implication postprocessing: " + stopwatch.format());

    // Add implications
    stopwatch.reset();
    fileio_progress.clear();
    System.out.println("Creating implications ");
    debugProgress.fine ("Adding Implications ... ");
    for (Iterator itor = all_ppts.pptIterator() ; itor.hasNext() ; ) {
      PptTopLevel ppt = (PptTopLevel) itor.next();
      // debugProgress.fine ("  Adding Implications for " + ppt.name);
      ppt.addImplications();
    }
    debugProgress.fine ("Time spent adding implications: " + stopwatch.format());

    // debug print suppressed invariants
    if (false && debugStats.isLoggable (Level.FINE)) {
      for (Iterator i = all_ppts.ppt_all_iterator(); i.hasNext(); ) {
        PptTopLevel ppt = (PptTopLevel) i.next();
        if (ppt.name().indexOf ("EXIT42") >= 0) {
          System.out.println (ppt.name() + " After final suppression");
          ppt.print_suppressed_invs (debugStats);
        }
      }
    }
  }

  private static void suppressWithSimplify(PptMap all_ppts) {
    System.out.print("Invoking Simplify to identify redundant invariants");
    System.out.flush();
    stopwatch.reset();
    for (Iterator itor = all_ppts.ppt_all_iterator() ; itor.hasNext() ; ) {
      PptTopLevel ppt = (PptTopLevel) itor.next();
      ppt.mark_implied_via_simplify(all_ppts);
      System.out.print(".");
      System.out.flush();
    }
    System.out.println(stopwatch.format());
  }

  public static void setup_NISuppression() {

    NIS.init_ni_suppression();
  }

  public static void setupEquality (PptMap allPpts) {

    // PptSliceEquality does all the necessary instantiations
    if (Daikon.use_equality_optimization) {

      // Foreach program point
      for (Iterator i = allPpts.pptIterator(); i.hasNext(); ) {
        PptTopLevel ppt = (PptTopLevel) i.next();

        if (dkconfig_df_bottom_up) {
          // Skip points that are not leaves
          if (use_dataflow_hierarchy) {
            if (!ppt.ppt_name.isGlobalPoint()
                && !ppt.ppt_name.isNumberedExitPoint())
              continue;
          }

          // setup equality on the splitters of a point with splitters
          if (ppt.has_splitters()) {
            for (Iterator ii = ppt.cond_iterator(); ii.hasNext(); ) {
              PptConditional ppt_cond = (PptConditional) ii.next();
              ppt_cond.equality_view = new PptSliceEquality(ppt_cond);
              ppt_cond.equality_view.instantiate_invariants();
            }
            if (use_dataflow_hierarchy)
              continue;
          }
        }

        // Create the initial equality sets
        ppt.equality_view = new PptSliceEquality(ppt);
        ppt.equality_view.instantiate_invariants();
      }
    }

  }

  public static void create_splitters(PptMap all_ppts, Set spinfo_files)
    throws IOException {
    for (Iterator i = spinfo_files.iterator(); i.hasNext(); ) {
      File filename = (File) i.next();
      SplitterObject[][] splitterObjectArrays
        = SplitterFactory.read_spinfofile(filename, all_ppts);
      for (int j = 0; j < splitterObjectArrays.length; j++) {
        int numsplitters = splitterObjectArrays[j].length;
        if (numsplitters == 0)
          continue; // Why do we have this entry in the array, anyway? -smcc
        String pptname = splitterObjectArrays[j][0].getPptName();
        Vector splitters = new Vector();
        for (int k = 0; k < numsplitters; k++) {
          if (splitterObjectArrays[j][k].splitterExists()) {
            splitters.addElement(splitterObjectArrays[j][k].getSplitter());
          } else {
            System.out.println(splitterObjectArrays[j][k].getError());
          }
        }

        if (splitters.size() >= 1) {
          // If the pptname is ALL, associate it with all program points.
          if (pptname.equals("ALL")) {
            SplitterList.put(".*", (Splitter[]) splitters.toArray(new Splitter[0]));
          } else {
            SplitterList.put( pptname, (Splitter[])splitters.toArray(new Splitter[0]));
          }
        }
      }
    }
  }

  // Guard the invariants at all PptTopLevels. Note that this changes
  // the contents of the PptTopLevels, and the changes made should
  // probably not be written out to an inv file (save the file before
  // this is called).
  public static void guardInvariants(PptMap allPpts) {
    for (Iterator i=allPpts.asCollection().iterator(); i.hasNext(); ) {
      PptTopLevel ppt = (PptTopLevel)i.next();
      if (ppt.num_samples() == 0)
        continue;
      // Make sure isDerivedParam is set before guarding.  Otherwise
      // we'll never get it correct.
      for (int iVarInfo = 0; iVarInfo < ppt.var_infos.length; iVarInfo++) {
        boolean temp = ppt.var_infos[iVarInfo].isDerivedParamAndUninteresting();
      }

      ppt.guardInvariants();
    }
  }

  private static void processOmissions(PptMap allPpts) {
    if (omit_types['0'])
      allPpts.removeUnsampled();
    for (Iterator i = allPpts.asCollection().iterator(); i.hasNext(); ) {
      PptTopLevel ppt = (PptTopLevel)i.next();
      ppt.processOmissions(omit_types);
    }
  }

  /**
   * Returns the max ppt that corresponds to the specified percentage
   * of ppts (presuming that only those ppts <= max_ppt will be
   * processed).
   */
  private static String setup_ppt_perc (Collection decl_files, int ppt_perc) {

    // Make sure the percentage is valid
    if ((ppt_perc < 1) || (ppt_perc > 100))
      throw new Error ("ppt_perc of " + ppt_perc + " is out of range 1..100");
    if (ppt_perc == 100)
      return null;

    // Keep track of all of the ppts in a set ordered by the ppt name
    Set ppts = new TreeSet();

    // Read all of the ppt names out of the decl files
    try {
      for (Iterator i = decl_files.iterator(); i.hasNext(); ) {
        File file = (File) i.next();

        // Open the file
        LineNumberReader fp = UtilMDE.LineNumberFileReader(file);

        // Read each ppt name from the file
        for (String line = fp.readLine(); line != null; line = fp.readLine()) {
          if (line.equals("") || FileIO.isComment(line))
            continue;
          if (!line.equals ("DECLARE"))
            continue;
          String ppt_name = fp.readLine();
          ppts.add (ppt_name);
        }

        fp.close();
      }
    } catch (IOException e) {
      e.printStackTrace();
      throw new Error (e);
    }

    // Determine the ppt_name that matches the specified percentage.  Always
    // return the last exit point from the method (so we don't get half the
    // exits from a method or enters without exits, etc)
    int ppt_cnt = (ppts.size() * ppt_perc) / 100;
    if (ppt_cnt == 0)
      throw new Error ("ppt_perc of " + ppt_perc + " over " + ppts.size()
                       + " results in 0 ppts to process");
    for (Iterator i = ppts.iterator(); i.hasNext(); ) {
      String ppt_name = (String) i.next();
      if (--ppt_cnt <= 0) {
        String last_ppt_name = ppt_name;
        while (i.hasNext()) {
          ppt_name = (String) i.next();
          if ((last_ppt_name.indexOf ("EXIT") != -1)
              && (ppt_name.indexOf ("EXIT") == -1))
            return (last_ppt_name);
          last_ppt_name = ppt_name;
        }
        return (ppt_name);
      }
    }
    throw new Error ("ppt_cnt " + ppt_cnt + " ppts.size " + ppts.size());
  }
}
