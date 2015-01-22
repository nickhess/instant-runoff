package org.bioluminati.irv;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;

/**
 * Issue
 * User: nhess
 * Date: 1/15/15
 * Time: 5:58 PM
 */
public class Issue {
    private final String issueName;
    private final String description;
    private List<Candidate> activeCandidates;
    private List<Candidate> eliminatedCandidates;
    private Map<String, Candidate> candidatesByNames;
    private Set<Vote> allVotes;

    private int nextId = 0;
    private Candidate _NONE;

    public Issue(String issueName, String description) {
        this.issueName = issueName;
        this.description = description;

        activeCandidates = new ArrayList<Candidate>();
        eliminatedCandidates = new ArrayList<Candidate>();
        candidatesByNames = new HashMap<String, Candidate>();

        _NONE = addCandidate("_NONE", "none");
        _NONE.eliminate();

        allVotes = new HashSet<Vote>();
    }

    public String getIssueName() {
        return issueName;
    }

    public String getDescription() {
        return description;
    }

    public Candidate addCandidate(String title, String description) {
        Candidate candidate = new Candidate(title, description);
        activeCandidates.add(candidate);
        candidatesByNames.put(toKey(candidate.getName()), candidate);
        println("++ Added candidate " + candidate);

        return candidate;
    }

    public Vote addVote(String voterName, List<String> candidateNames) {
        List<Candidate> votes = new ArrayList<Candidate>();
        for (String name : candidateNames) {
            Candidate candidate = candidatesByNames.get(toKey(name));
            if (null == candidate)
                throw new RuntimeException("Unmatched candidate name " + name + " on vote by " + voterName);
            votes.add(candidate);
        }
        Vote vote = new Vote(voterName, votes);
        allVotes.add(vote);
        println("++ Added vote " + vote);
        vote.assign();
        return vote;
    }

    // the main process
    public Candidate process() {
        int round = 1;
        while (!weHaveAWinner()) {
            println("** Round " + round++);
            showTable();
            Candidate bottomCandidate;
            int bottomVotes;
            do {
                bottomCandidate = bottomCandidate();
                bottomVotes = bottomCandidate.votes.size();
                bottomCandidate.eliminate();
            }
            while (0 == bottomVotes); // will purge all the 0s on the first run
        }
        return topCandidate();
    }

    // Candidate class
    class Candidate {
        private final int id;
        private final String name;
        private final String description;
        private Set<Vote> votes;
        private boolean eliminated = false;

        public Candidate(String name, String description) {
            this.id = Issue.this.nextId++;
            this.name = name;
            this.description = description;
            this.votes = new HashSet<Vote>();
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public boolean isEliminated() {
            return eliminated;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Candidate candidate = (Candidate) o;

            if (id != candidate.id) return false;
            return true;
        }

        public void addVote(Vote vote) {
            // this should never happen
            if (this.eliminated)
                throw new RuntimeException("Attempted to assign vote " + vote + " to eliminated candidate " + this);

            // also, this should never happen
            if (!this.equals(vote.getCurrentCandidate()))
                throw new RuntimeException("Attempted to assign vote " + vote + " to candidate " + this);

            this.votes.add(vote);
        }

        public void eliminate() {
            println("-- Eliminating: '" + this + "'");
            Issue.this.activeCandidates.remove(this);
            Issue.this.eliminatedCandidates.add(this);
            for (Vote vote : votes)
                vote.reassign();
            votes.clear();

            this.eliminated = !_NONE.equals(this);// we want to assign eliminated votes to NONE
        }


        @Override
        public String toString() {
            return name + "(" + votes.size() +
                    " votes)";
        }
    }

    // Vote class
    class Vote {
        private final int id;
        private final String voterName;
        private final List<Candidate> votes;

        private int currentChoiceIndex = 0;
        private final int numChoices;

        public Vote(String voterName, List<Candidate> votes) {
            this.id = Issue.this.nextId++;
            this.voterName = voterName;

            this.votes = checkUnique(votes);
            this.numChoices = votes.size();
            this.currentChoiceIndex = 0;
        }

        public String getVoterName() {
            return voterName;
        }

        public List<Candidate> getVotes() {
            return votes;
        }

        public int getId() {
            return id;
        }

        public Candidate getCurrentCandidate() {
            if (numChoices <= currentChoiceIndex) return Issue.this._NONE;

            // This will throw OOBException if we reassign past the end of choices.
            // I want that to happen, it'll reveal fencepost error.
            // Better death than corruption
            return votes.get(currentChoiceIndex);
        }

        public Candidate assign() {
            getCurrentCandidate().addVote(this);
            println(">> Assigned " + voterName + "'s vote to choice#" + (currentChoiceIndex + 1) + ", " + getCurrentCandidate());
            return getCurrentCandidate();
        }

        public Candidate reassign() {
            // this shouldn't happen
            if (numChoices <= currentChoiceIndex)
                throw new RuntimeException("Attempting to reassign already-dropped vote");

            do {
                ++currentChoiceIndex;
            } while (getCurrentCandidate().isEliminated() && numChoices > currentChoiceIndex);
            return assign();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Vote vote = (Vote) o;

            if (id != vote.id) return false;

            return true;
        }

        @Override
        public String toString() {
            return
                    voterName +
                            ": votes=" + votes +
                            ", current choice#" + (currentChoiceIndex + 1) +
                            '}';
        }
    }


    public static class CandidateRankerByVote implements Comparator<Issue.Candidate> {
        public static CandidateRankerByVote CANDIDATE_RANKER = new CandidateRankerByVote();

        @Override
        public int compare(Candidate candidate, Candidate candidate2) {
            return candidate2.votes.size() - candidate.votes.size();
        }

    }

    // util methods

    static void println(Object o) {
        System.out.println(o);
    }

    public void showTable() {
        println("Candidates ");
        for (Candidate candidate : activeCandidates) {
            println("  " + candidate);
        }
    }

    private Candidate topCandidate() {
        return activeCandidates.get(0);
    }

    private Candidate bottomCandidate() {
        return activeCandidates.get(activeCandidates.size() - 1);
    }

    private boolean weHaveAWinner() {
        Collections.sort(activeCandidates, CandidateRankerByVote.CANDIDATE_RANKER);
        int numActiveVotes = allVotes.size() - _NONE.votes.size();
        float leadPercentage = ((float) topCandidate().votes.size() / numActiveVotes);
        return (0.5 <= leadPercentage);
    }

    static <E> List<E> checkUnique(List<E> options) {
        LinkedHashSet<E> set = new LinkedHashSet<E>(options);
        if (set.size() != options.size())
            throw new RuntimeException("Input list " + options + " has duplicates; please check");
        return Collections.unmodifiableList(options);
    }

    static String toKey(String input) {
        return input.toUpperCase().trim();
    }

    // main

    public final static void main(String[] args) throws Exception {

        File fin = new File(args[0]);
        BufferedReader br = new BufferedReader(new FileReader(fin));

        Issue issue = new Issue("PYB name vote", "New name for PYB");

        String line = null;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] bits = line.split("\\s*:\\s*");
            String lineType = toKey(bits[0]);
            if ("CANDIDATE".equals(lineType)) {
                String candidate = bits[1];
                issue.addCandidate(candidate, "");
            } else if ("VOTE".equals(lineType)) {
                String voterName = bits[1];
                List<String> votes = Arrays.asList(bits[2].split(","));
                issue.addVote(voterName, votes);
            } else {
                System.err.print("UNPARSED LINE: " + line);
                return;
            }

        }
        br.close();

        Candidate winner = issue.process();
        println("*** WINNER ***");
        issue.showTable();
        println("WINNER:" + winner);
        println(issue.allVotes.size() + " votes total, " + issue._NONE.votes.size() + " votes dropped.");
    }

    // test methods

    public final static void test(String[] args) {
        Issue issue = new Issue("TEST", "test issue");
        issue.addCandidate("Bush", "R");
        issue.addCandidate("Gore", "D");
        issue.addCandidate("Nader", "G");
        issue.addCandidate("Browne", "L");

        createMassVotes(issue, "con", 6, "BUSH");
        createMassVotes(issue, "bizcon", 3, "browne", "Bush");
        createMassVotes(issue, "liberal", 6, "gore");
        createMassVotes(issue, "techie", 2, "browne", "nader", "gore");
        createMassVotes(issue, "green", 2, "nader", "gore");

        Candidate winner = issue.process();
        System.out.println("WINNER: " + winner);
    }

    private static final void createMassVotes(Issue issue, String voterType, int numvotes, String... candidates) {
        List<String> votefor = Arrays.asList(candidates);
        for (int loop = 1; loop <= numvotes; loop++) {
            issue.addVote(voterType + loop, votefor);
        }

    }
}
