import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/*
 * TODO Possible extensions:
 * - Include consensus signatures and tell by which Tor versions the
 *   consensus will be accepted (and by which not)
 */
public class ConsensusHealthChecker {

  private String mostRecentValidAfterTime = null;

  private byte[] mostRecentConsensus = null;

  private SortedMap<String, byte[]> mostRecentVotes =
        new TreeMap<String, byte[]>();

  public void processConsensus(String validAfterTime, byte[] data) {
    if (this.mostRecentValidAfterTime == null ||
        this.mostRecentValidAfterTime.compareTo(validAfterTime) < 0) {
      this.mostRecentValidAfterTime = validAfterTime;
      this.mostRecentVotes.clear();
      this.mostRecentConsensus = data;
    }
  }

  public void processVote(String validAfterTime, String dirSource,
      byte[] data) {
    if (this.mostRecentValidAfterTime == null ||
        this.mostRecentValidAfterTime.compareTo(validAfterTime) < 0) {
      this.mostRecentValidAfterTime = validAfterTime;
      this.mostRecentVotes.clear();
      this.mostRecentConsensus = null;
    }
    if (this.mostRecentValidAfterTime.equals(validAfterTime)) {
      this.mostRecentVotes.put(dirSource, data);
    }
  }

  public void writeStatusWebsite() {

    /* If we don't have any consensus, we cannot write useful consensus
     * health information to the website. Do not overwrite existing page
     * with a warning, because we might just not have learned about a new
     * consensus in this execution. */
    if (this.mostRecentConsensus == null) {
      return;
    }

    /* Prepare parsing dates. */
    SimpleDateFormat dateTimeFormat =
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    StringBuilder knownFlagsResults = new StringBuilder();
    StringBuilder consensusMethodsResults = new StringBuilder();
    StringBuilder versionsResults = new StringBuilder();
    StringBuilder paramsResults = new StringBuilder();
    StringBuilder authorityKeysResults = new StringBuilder();
    StringBuilder bandwidthScannersResults = new StringBuilder();
    
    /* Read consensus and parse all information that we want to compare to
     * votes. */
    String consensusConsensusMethod = null, consensusKnownFlags = null,
        consensusClientVersions = null, consensusServerVersions = null,
        consensusParams = null;
    Scanner s = new Scanner(new String(this.mostRecentConsensus));
    while (s.hasNextLine()) {
      String line = s.nextLine();
      if (line.startsWith("consensus-method ")) {
        consensusConsensusMethod = line;
      } else if (line.startsWith("client-versions ")) {
        consensusClientVersions = line;
      } else if (line.startsWith("server-versions ")) {
        consensusServerVersions = line;
      } else if (line.startsWith("known-flags ")) {
        consensusKnownFlags = line;
      } else if (line.startsWith("params ")) {
        consensusParams = line;
      }
    }
    s.close();

    /* Read votes and parse all information to compare with the
     * consensus. */
    for (byte[] voteBytes : this.mostRecentVotes.values()) {
      String voteConsensusMethods = null, voteKnownFlags = null,
          voteClientVersions = null, voteServerVersions = null,
          voteParams = null, voteDirSourceLine = null,
          voteDirKeyExpires = null;
      int voteContainsBandwidthWeights = 0;
      s = new Scanner(new String(voteBytes));
      while (s.hasNextLine()) {
        String line = s.nextLine();
        if (line.startsWith("consensus-methods ")) {
          voteConsensusMethods = line;
        } else if (line.startsWith("client-versions ")) {
          voteClientVersions = line;
        } else if (line.startsWith("server-versions ")) {
          voteServerVersions = line;
        } else if (line.startsWith("known-flags ")) {
          voteKnownFlags = line;
        } else if (line.startsWith("params ")) {
          voteParams = line;
        } else if (line.startsWith("dir-source ")) {
          voteDirSourceLine = line;
        } else if (line.startsWith("dir-key-expires ")) {
          voteDirKeyExpires = line;
        } else if (line.startsWith("w ")) {
          if (line.contains(" Measured")) {
            voteContainsBandwidthWeights++;
          }
        }
      }
      s.close();

      /* Remember authority nickname. */
      String dirSource = voteDirSourceLine.split(" ")[1];

      /* Write known flags. */
      knownFlagsResults.append("          <tr>\n"
          + "            <td>" + dirSource + "</td>\n"
          + "            <td>" + voteKnownFlags + "</td>\n"
          + "          </tr>\n");
      
      /* Write supported consensus methods. */
      if (!voteConsensusMethods.contains(consensusConsensusMethod.
          split(" ")[1])) {
        consensusMethodsResults.append("          <tr>\n"
            + "            <td><font color=\"red\">" + dirSource
              + "</font></td>\n"
            + "            <td><font color=\"red\">"
              + voteConsensusMethods + "</font></td>\n"
            + "          </tr>\n");
      } else {
        consensusMethodsResults.append("          <tr>\n"
               + "            <td>" + dirSource + "</td>\n"
               + "            <td>" + voteConsensusMethods + "</td>\n"
               + "          </tr>\n");
      }

      /* Write recommended versions. */
      if (voteClientVersions == null) {
        /* Not a versioning authority. */
      } else if (!voteClientVersions.equals(consensusClientVersions)) {
        versionsResults.append("          <tr>\n"
            + "            <td><font color=\"red\">" + dirSource
              + "</font></td>\n"
            + "            <td><font color=\"red\">"
              + voteClientVersions + "</font></td>\n"
            + "          </tr>\n");
      } else {
        versionsResults.append("          <tr>\n"
            + "            <td>" + dirSource + "</td>\n"
            + "            <td>" + voteClientVersions + "</td>\n"
            + "          </tr>\n");
      }
      if (voteServerVersions == null) {
        /* Not a versioning authority. */
      } else if (!voteServerVersions.equals(consensusServerVersions)) {
        versionsResults.append("          <tr>\n"
            + "            <td/>\n"
            + "            <td><font color=\"red\">"
              + voteClientVersions + "</font></td>\n"
            + "          </tr>\n");
      } else {
        versionsResults.append("          <tr>\n"
            + "            <td/>\n"
            + "            <td>" + voteServerVersions + "</td>\n"
            + "          </tr>\n");
      }

      /* Write consensus parameters. */
      if (voteParams == null) {
        /* Authority doesn't set consensus parameters. */
      } else if (!voteParams.equals(consensusParams)) {
        paramsResults.append("          <tr>\n"
            + "            <td><font color=\"red\">" + dirSource
              + "</font></td>\n"
            + "            <td><font color=\"red\">"
              + voteParams + "</font></td>\n"
            + "          </tr>\n");
      } else {
        paramsResults.append("          <tr>\n"
            + "            <td>" + dirSource + "</td>\n"
            + "            <td>" + voteParams + "</td>\n"
            + "          </tr>\n");
      }
      
      /* Write authority key expiration date. */
      if (voteDirKeyExpires != null) {
        boolean expiresIn14Days = false;
        try {
          expiresIn14Days = (System.currentTimeMillis()
              + 14L * 24L * 60L * 60L * 1000L >
              dateTimeFormat.parse(voteDirKeyExpires.substring(
              "dir-key-expires ".length())).getTime());
        } catch (ParseException e) {
          /* Can't parse the timestamp? Whatever. */
        }
        if (expiresIn14Days) {
          authorityKeysResults.append("          <tr>\n"
              + "            <td><font color=\"red\">" + dirSource
                + "</font></td>\n"
              + "            <td><font color=\"red\">"
                + voteDirKeyExpires + "</font></td>\n"
              + "          </tr>\n");
        } else {
          authorityKeysResults.append("          <tr>\n"
              + "            <td>" + dirSource + "</td>\n"
              + "            <td>" + voteDirKeyExpires + "</td>\n"
              + "          </tr>\n");
        }
      }

      /* Write results for bandwidth scanner status. */
      if (voteContainsBandwidthWeights > 0) {
        bandwidthScannersResults.append("          <tr>\n"
            + "            <td>" + dirSource + "</td>\n"
            + "            <td>" + voteContainsBandwidthWeights
              + " Measured values in w lines<td/>\n"
            + "          </tr>\n");
      }
    }

    try {

      /* Start writing web page. */
      BufferedWriter bw = new BufferedWriter(
          new FileWriter("website/consensus-health.html"));
      bw.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.0 "
            + "Transitional//EN\">\n"
          + "<html>\n"
          + "  <head>\n"
          + "    <title>Tor Metrics Portal: Consensus health</title>\n"
          + "    <meta http-equiv=Content-Type content=\"text/html; "
            + "charset=iso-8859-1\">\n"
          + "    <link href=\"http://www.torproject.org/stylesheet-"
          + "ltr.css\" type=text/css rel=stylesheet>\n"
          + "    <link href=\"http://www.torproject.org/favicon.ico\""
            + " type=image/x-icon rel=\"shortcut icon\">\n"
          + "  </head>\n"
          + "  <body>\n"
          + "    <div class=\"center\">\n"
          + "      <table class=\"banner\" border=\"0\" "
            + "cellpadding=\"0\" cellspacing=\"0\" summary=\"\">\n"
          + "        <tr>\n"
          + "          <td class=\"banner-left\"><a href=\"https://"
            + "www.torproject.org/\"><img src=\"http://www.torproject"
            + ".org/images/top-left.png\" alt=\"Click to go to home "
            + "page\" width=\"193\" height=\"79\"></a></td>\n"
          + "          <td class=\"banner-middle\">\n"
          + "            <a href=\"/\">Home</a>\n"
          + "            <a href=\"graphs.html\">Graphs</a>\n"
          + "            <a href=\"reports.html\">Reports</a>\n"
          + "            <a href=\"papers.html\">Papers</a>\n"
          + "            <a href=\"data.html\">Data</a>\n"
          + "            <a href=\"tools.html\">Tools</a>\n"
          + "          </td>\n"
          + "          <td class=\"banner-right\"></td>\n"
          + "        </tr>\n"
          + "      </table>\n"
          + "      <div class=\"main-column\">\n"
          + "        <h2>Tor Metrics Portal: Consensus Health</h2>\n"
          + "        <br/>\n"
          + "        <p>This page shows statistics about the current "
            + "consensus and votes to facilitate debugging of the "
            + "directory consensus process.</p>\n");

      /* Write valid-after time. */
      bw.write("        <br/>\n"
          + "        <h3>Valid-after time</h3>\n"
          + "        <br/>\n"
          + "        <p>Consensus was published ");
      boolean consensusIsStale = false;
      try {
        consensusIsStale = System.currentTimeMillis()
            - 3L * 60L * 60L * 1000L >
            dateTimeFormat.parse(this.mostRecentValidAfterTime).getTime();
      } catch (ParseException e) {
        /* Can't parse the timestamp? Whatever. */
      }
      if (consensusIsStale) {
        bw.write("<font color=\"red\">" + this.mostRecentValidAfterTime
            + "</font>");
      } else {
        bw.write(this.mostRecentValidAfterTime);
      }
      bw.write(". <i>Note that it takes "
            + "15 to 30 minutes for the metrics portal to learn about "
            + "new consensus and votes and process them.</i></p>\n");

      /* Write known flags. */
      bw.write("        <br/>\n"
          + "        <h3>Known flags</h3>\n"
          + "        <br/>\n"
          + "        <table border=\"0\" cellpadding=\"4\" "
          + "cellspacing=\"0\" summary=\"\">\n"
          + "          <colgroup>\n"
          + "            <col width=\"160\">\n"
          + "            <col width=\"640\">\n"
          + "          </colgroup>\n");
      if (knownFlagsResults.length() < 1) {
        bw.write("          <tr><td>(No votes.)</td><td/></tr>\n");
      } else {
        bw.write(knownFlagsResults.toString());
      }
      bw.write("          <td><font color=\"blue\">consensus</font>"
            + "</td>\n"
          + "            <td><font color=\"blue\">"
            + consensusKnownFlags + "</font></td>\n"
          + "          </tr>\n");
      bw.write("        </table>\n");

      /* Write consensus methods. */
      bw.write("        <br/>\n"
          + "        <h3>Consensus methods</h3>\n"
          + "        <br/>\n"
          + "        <table border=\"0\" cellpadding=\"4\" "
          + "cellspacing=\"0\" summary=\"\">\n"
          + "          <colgroup>\n"
          + "            <col width=\"160\">\n"
          + "            <col width=\"640\">\n"
          + "          </colgroup>\n");
      if (consensusMethodsResults.length() < 1) {
        bw.write("          <tr><td>(No votes.)</td><td/></tr>\n");
      } else {
        bw.write(consensusMethodsResults.toString());
      }
      bw.write("          <td><font color=\"blue\">consensus</font>"
            + "</td>\n"
          + "            <td><font color=\"blue\">"
            + consensusConsensusMethod + "</font></td>\n"
          + "          </tr>\n");
      bw.write("        </table>\n");
        
      /* Write recommended versions. */
      bw.write("        <br/>\n"
          + "        <h3>Recommended versions</h3>\n"
          + "        <br/>\n"
          + "        <table border=\"0\" cellpadding=\"4\" "
          + "cellspacing=\"0\" summary=\"\">\n"
          + "          <colgroup>\n"
          + "            <col width=\"160\">\n"
          + "            <col width=\"640\">\n"
          + "          </colgroup>\n");
      if (versionsResults.length() < 1) {
        bw.write("          <tr><td>(No votes.)</td><td/></tr>\n");
      } else {
        bw.write(versionsResults.toString());
      }
      bw.write("          <td><font color=\"blue\">consensus</font>"
          + "</td>\n"
          + "            <td><font color=\"blue\">"
            + consensusClientVersions + "</font></td>\n"
          + "          </tr>\n");
      bw.write("          <td/>\n"
          + "            <td><font color=\"blue\">"
          + consensusServerVersions + "</font></td>\n"
        + "          </tr>\n");
      bw.write("        </table>\n");
        
      /* Write consensus parameters. */
      bw.write("        <br/>\n"
          + "        <h3>Consensus parameters</h3>\n"
          + "        <br/>\n"
          + "        <table border=\"0\" cellpadding=\"4\" "
          + "cellspacing=\"0\" summary=\"\">\n"
          + "          <colgroup>\n"
          + "            <col width=\"160\">\n"
          + "            <col width=\"640\">\n"
          + "          </colgroup>\n");
      if (paramsResults.length() < 1) {
        bw.write("          <tr><td>(No votes.)</td><td/></tr>\n");
      } else {
        bw.write(paramsResults.toString());
      }
      bw.write("          <td><font color=\"blue\">consensus</font>"
          + "</td>\n"
        + "            <td><font color=\"blue\">"
          + consensusParams + "</font></td>\n"
        + "          </tr>\n");
      bw.write("        </table>\n");
        
      /* Write authority keys. */
      bw.write("        <br/>\n"
          + "        <h3>Authority keys</h3>\n"
          + "        <br/>\n"
          + "        <table border=\"0\" cellpadding=\"4\" "
          + "cellspacing=\"0\" summary=\"\">\n"
          + "          <colgroup>\n"
          + "            <col width=\"160\">\n"
          + "            <col width=\"640\">\n"
          + "          </colgroup>\n");
      if (authorityKeysResults.length() < 1) {
        bw.write("          <tr><td>(No votes.)</td><td/></tr>\n");
      } else {
        bw.write(authorityKeysResults.toString());
      }
      bw.write("        </table>\n"
          + "        <br/>\n"
          + "        <p><i>Note that expiration dates of legacy keys are "
            + "not included in votes and therefore not listed here!</i>"
            + "</p>\n");
        
      /* Write bandwidth scanner status. */
      bw.write("        <br/>\n"
           + "        <h3>Bandwidth scanner status</h3>\n"
          + "        <br/>\n"
          + "        <table border=\"0\" cellpadding=\"4\" "
          + "cellspacing=\"0\" summary=\"\">\n"
          + "          <colgroup>\n"
          + "            <col width=\"160\">\n"
          + "            <col width=\"640\">\n"
          + "          </colgroup>\n");
      if (bandwidthScannersResults.length() < 1) {
        bw.write("          <tr><td>(No votes.)</td><td/></tr>\n");
      } else {
        bw.write(bandwidthScannersResults.toString());
      }
      bw.write("        </table>\n");

      /* Finish writing. */
      bw.write("      </div>\n"
          + "    </div>\n"
          + "    <div class=\"bottom\" id=\"bottom\">\n"
          + "      <p>\"Tor\" and the \"Onion Logo\" are <a "
            + "href=\"https://www.torproject.org/trademark-faq.html"
            + ".en\">"
          + "registered trademarks</a> of The Tor Project, "
            + "Inc.</p>\n"
          + "    </div>\n"
          + "  </body>\n"
          + "</html>");
      bw.close();

    } catch (IOException e) {
    }
  }
}
