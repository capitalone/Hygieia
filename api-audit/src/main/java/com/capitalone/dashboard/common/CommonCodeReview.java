package com.capitalone.dashboard.common;

import com.capitalone.dashboard.ApiSettings;
import com.capitalone.dashboard.model.CodeAction;
import com.capitalone.dashboard.model.CodeActionType;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.Comment;
import com.capitalone.dashboard.model.Commit;
import com.capitalone.dashboard.model.CommitStatus;
import com.capitalone.dashboard.model.GitRequest;
import com.capitalone.dashboard.model.Review;
import com.capitalone.dashboard.model.SCM;
import com.capitalone.dashboard.repository.CommitRepository;
import com.capitalone.dashboard.response.AuditReviewResponse;
import com.capitalone.dashboard.response.CodeReviewAuditResponse;
import com.capitalone.dashboard.response.CodeReviewAuditResponseV2;
import com.capitalone.dashboard.status.CodeReviewAuditStatus;
import com.capitalone.dashboard.util.GitHubParsedUrl;
import com.google.common.collect.Sets;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ldap.support.LdapUtils;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class CommonCodeReview {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommonCodeReview.class);

    /**
     * Calculates the peer review status for a given pull request
     *
     * @param pr                  - pull request
     * @param auditReviewResponse - audit review response
     * @return boolean fail or pass
     */
    public static boolean computePeerReviewStatus(GitRequest pr, ApiSettings settings,
                                                  AuditReviewResponse<CodeReviewAuditStatus> auditReviewResponse,
                                                  List<Commit> commits) {
        List<Review> reviews = pr.getReviews();

        List<CommitStatus> statuses = pr.getCommitStatuses();

        Map<String, String> actors = getActors(pr);

        if (!CollectionUtils.isEmpty(statuses)) {
            String contextString = settings.getPeerReviewContexts();
            Set<String> prContexts = StringUtils.isEmpty(contextString) ? new HashSet<>() : Sets.newHashSet(contextString.trim().split(","));

            boolean lgtmAttempted = false;
            boolean lgtmStateResult = false;
            for (CommitStatus status : statuses) {
                if (status.getContext() != null && prContexts.contains(status.getContext())) {
                    //review done using LGTM workflow assuming its in the settings peerReviewContexts
                    lgtmAttempted = true;
                    String stateString = (status.getState() != null) ? status.getState().toLowerCase() : "unknown";
                    switch (stateString) {
                        case "pending":
                            auditReviewResponse.addAuditStatus(CodeReviewAuditStatus.PEER_REVIEW_LGTM_PENDING);
                            break;

                        case "error":
                            auditReviewResponse.addAuditStatus(CodeReviewAuditStatus.PEER_REVIEW_LGTM_PENDING);
                            break;

                        case "success":
                            lgtmStateResult = true;
                            auditReviewResponse.addAuditStatus(CodeReviewAuditStatus.PEER_REVIEW_LGTM_SUCCESS);

                            String description = status.getDescription();
                            if (!StringUtils.isEmpty(settings.getServiceAccountOU()) && !StringUtils.isEmpty(settings.getPeerReviewApprovalText()) && !StringUtils.isEmpty(description) &&
                                    description.startsWith(settings.getPeerReviewApprovalText())) {
                                String user = description.replace(settings.getPeerReviewApprovalText(), "").trim();
                                if (!StringUtils.isEmpty(actors.get(user)) && checkForServiceAccount(actors.get(user), settings)) {
                                    auditReviewResponse.addAuditStatus(CodeReviewAuditStatus.PEER_REVIEW_BY_SERVICEACCOUNT);
                                }
                            }
                            break;

                        default:
                            auditReviewResponse.addAuditStatus(CodeReviewAuditStatus.PEER_REVIEW_LGTM_UNKNOWN);
                            break;
                    }
                }
            }

            if (lgtmAttempted) {
                //if lgtm self-review, then no peer-review was done unless someone else looked at it
                if (!CollectionUtils.isEmpty(auditReviewResponse.getAuditStatuses()) &&
                        !isPRReviewedInTimeScale(pr, auditReviewResponse, commits)) {
                    auditReviewResponse.addAuditStatus(CodeReviewAuditStatus.PEER_REVIEW_LGTM_SELF_APPROVAL);
                    return false;
                }
                return lgtmStateResult;
            }
        }


        if (!CollectionUtils.isEmpty(reviews)) {
            for (Review review : reviews) {
                if ("approved".equalsIgnoreCase(review.getState())) {
                    if (!StringUtils.isEmpty(review.getAuthorLDAPDN()) && checkForServiceAccount(review.getAuthorLDAPDN(), settings)) {
                        auditReviewResponse.addAuditStatus(CodeReviewAuditStatus.PEER_REVIEW_BY_SERVICEACCOUNT);
                    }
                    //review done using GitHub Review workflow
                    auditReviewResponse.addAuditStatus(CodeReviewAuditStatus.PEER_REVIEW_GHR);
                    if (!CollectionUtils.isEmpty(auditReviewResponse.getAuditStatuses()) &&
                            !isPRReviewedInTimeScale(pr, auditReviewResponse, commits)) {
                        auditReviewResponse.addAuditStatus(CodeReviewAuditStatus.PEER_REVIEW_GHR_SELF_APPROVAL);
                        return false;
                    }
                    return true;
                }
            }
        }

        return false;
    }


    public static boolean checkForServiceAccount(String userLdapDN, ApiSettings settings) {
        if (!StringUtils.isEmpty(settings.getServiceAccountOU())) {
            try {
                return (settings.getServiceAccountOU().equalsIgnoreCase(LdapUtils.getStringValue(new LdapName(userLdapDN), "OU")));
            } catch (InvalidNameException e) {
                LOGGER.error("Error parsing LDAP DN:" + userLdapDN);
            }
        } else {
            LOGGER.info("API Settings missing service account RDN");
        }
        return false;
    }

    /**
     * Get all the actors associated with this user.s
     *
     * @param pr
     * @return
     */
    private static Map<String, String> getActors(GitRequest pr) {
        Map<String, String> actors = new HashMap<>();
        if (!StringUtils.isEmpty(pr.getMergeAuthorLDAPDN())) {
            actors.put(pr.getMergeAuthor(), pr.getMergeAuthorLDAPDN());
        }
        Optional.ofNullable(pr.getCommits()).orElse(Collections.emptyList()).stream().filter(c -> !StringUtils.isEmpty(c.getScmAuthorLDAPDN())).forEach(c -> actors.put(c.getScmAuthor(), c.getScmAuthorLDAPDN()));
        Optional.ofNullable(pr.getComments()).orElse(Collections.emptyList()).stream().filter(c -> !StringUtils.isEmpty(c.getUserLDAPDN())).forEach(c -> actors.put(c.getUser(), c.getUserLDAPDN()));
        Optional.ofNullable(pr.getReviews()).orElse(Collections.emptyList()).stream().filter(r -> !StringUtils.isEmpty(r.getAuthorLDAPDN())).forEach(r -> actors.put(r.getAuthor(), r.getAuthorLDAPDN()));
        return actors;
    }

    /**
     * Calculates if the PR was looked at by a peer
     *
     * @param pr
     * @return true if PR was looked at by at least one peer
     */
    private static boolean isPRLookedAtByPeer(GitRequest pr) {
        Set<String> commentUsers = pr.getComments() != null ? pr.getComments().stream().map(Comment::getUser).collect(Collectors.toCollection(HashSet::new)) : new HashSet<>();
        Set<String> reviewAuthors = pr.getReviews() != null ? pr.getReviews().stream().map(Review::getAuthor).collect(Collectors.toCollection(HashSet::new)) : new HashSet<>();
        reviewAuthors.remove("unknown");

        Set<String> prCommitAuthors = pr.getCommits() != null ? pr.getCommits().stream().map(Commit::getScmAuthorLogin).collect(Collectors.toCollection(HashSet::new)) : new HashSet<>();
        prCommitAuthors.add(pr.getUserId());
        prCommitAuthors.remove("unknown");

        commentUsers.removeAll(prCommitAuthors);
        reviewAuthors.removeAll(prCommitAuthors);

        return (commentUsers.size() > 0) || (reviewAuthors.size() > 0);
    }


    private static boolean isPRReviewedInTimeScale(GitRequest pr,
                                                   AuditReviewResponse<CodeReviewAuditStatus> auditReviewResponse,
                                                   List<Commit> commits) {
        List<Commit> filteredPrCommits = new ArrayList<>();
        pr.getCommits().forEach(prC -> {
            Optional<Commit> cOptionalCommit = commits.stream().filter(c -> Objects.equals(c.getScmRevisionNumber(), prC.getScmRevisionNumber())).findFirst();
            Commit cCommit = cOptionalCommit.orElse(null);

            if (cCommit != null
                    && !CollectionUtils.isEmpty(cCommit.getScmParentRevisionNumbers())
                    && cCommit.getScmParentRevisionNumbers().size() > 1) {
                //exclude commits with multiple parents ie. merge commits
            } else {
                String mergeCommitLog = String.format("Merge branch '%s' into %s", pr.getScmBranch(), pr.getSourceBranch());
                if (!prC.getScmCommitLog().contains(mergeCommitLog)) {
                    filteredPrCommits.add(prC);
                }
            }
        });

        List<CodeAction> codeActionList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(filteredPrCommits)) {
            codeActionList.addAll(filteredPrCommits.stream().map(c -> new CodeAction(CodeActionType.Commit, c.getScmCommitTimestamp(),
                    "unknown".equalsIgnoreCase(c.getScmAuthorLogin()) ? pr.getUserId() : c.getScmAuthorLogin(),
                    c.getScmAuthorLDAPDN() != null ? c.getScmAuthorLDAPDN() : "unknown", c.getScmCommitLog())).collect(Collectors.toList()));
        }
        if (!CollectionUtils.isEmpty(pr.getReviews())) {
            codeActionList.addAll(pr.getReviews().stream().map(r -> new CodeAction(CodeActionType.Review, r.getUpdatedAt(),
                    r.getAuthor(), r.getAuthorLDAPDN() != null ? r.getAuthorLDAPDN() : "unknown", r.getBody())).collect(Collectors.toList()));
        }
        if (!CollectionUtils.isEmpty(pr.getComments())) {
            codeActionList.addAll(pr.getComments().stream().map(r -> new CodeAction(CodeActionType.Review, r.getUpdatedAt(),
                    r.getUser(), r.getUserLDAPDN() != null ? r.getUserLDAPDN() : "unknown", r.getBody())).collect(Collectors.toList()));
        }

        codeActionList.add(new CodeAction(CodeActionType.PRMerge, pr.getMergedAt(), pr.getMergeAuthor(), pr.getMergeAuthorLDAPDN(), "merged"));
        codeActionList.add(new CodeAction(CodeActionType.PRCreate, pr.getCreatedAt(), pr.getUserId(), "unknown", "create"));

        codeActionList.sort(Comparator.comparing(CodeAction::getTimestamp));

        codeActionList.stream().forEach(c -> LOGGER.debug(new DateTime(c.getTimestamp()).toString("yyyy-MM-dd hh:mm:ss.SSa")
                + " " + c.getType() + " " + c.getActor() + " " + c.getMessage()));

        List<CodeAction> clonedCodeActions = codeActionList.stream().map(CodeAction::new).collect(Collectors.toList());
        if (auditReviewResponse instanceof CodeReviewAuditResponse) {
            ((CodeReviewAuditResponse) auditReviewResponse).setCodeActions(clonedCodeActions);
        } else if (auditReviewResponse instanceof CodeReviewAuditResponseV2.PullRequestAudit) {
            ((CodeReviewAuditResponseV2.PullRequestAudit) auditReviewResponse).setCodeActions(clonedCodeActions);
        }

        Set<CodeAction> reviewedList = new HashSet<>();
        codeActionList.stream().filter(as -> as.getType() == CodeActionType.Review).map(as -> getReviewedActions(codeActionList, as)).forEach(reviewedList::addAll);
        codeActionList.removeAll(reviewedList);
        return codeActionList.stream().noneMatch(as -> as.getType() == CodeActionType.Commit);
    }


    private static List<CodeAction> getReviewedActions(List<CodeAction> codeActionList, CodeAction reviewAction) {
        return codeActionList.stream()
                .filter(cal -> cal.getTimestamp() < reviewAction.getTimestamp())
                .filter(cal -> (cal.getType() == CodeActionType.Commit) && !reviewAction.getActor().equalsIgnoreCase(cal.getActor()))
                .collect(Collectors.toList());
    }


    public static Set<String> getCodeAuthors(List<CollectorItem> repoItems, long beginDate,
                                             long endDate, CommitRepository commitRepository) {
        Set<String> authors = new HashSet<>();
        //making sure we have a goot url?
        repoItems.forEach(repoItem -> {
            String scmUrl = (String) repoItem.getOptions().get("url");
            String scmBranch = (String) repoItem.getOptions().get("branch");
            GitHubParsedUrl gitHubParsed = new GitHubParsedUrl(scmUrl);
            String parsedUrl = gitHubParsed.getUrl(); //making sure we have a goot url?
            List<Commit> commits = commitRepository.findByCollectorItemIdAndScmCommitTimestampIsBetween(repoItem.getId(), beginDate - 1, endDate + 1);
            authors.addAll(commits.stream().map(SCM::getScmAuthor).collect(Collectors.toCollection(HashSet::new)));
        });
        return authors;
    }
}
