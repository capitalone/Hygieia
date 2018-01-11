package com.capitalone.dashboard.evaluator;

import com.capitalone.dashboard.common.CommonCodeReview;
import com.capitalone.dashboard.model.AuditException;
import com.capitalone.dashboard.model.AuditStatus;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.CollectorType;
import com.capitalone.dashboard.model.Commit;
import com.capitalone.dashboard.model.CommitType;
import com.capitalone.dashboard.model.Dashboard;
import com.capitalone.dashboard.model.GitRequest;
import com.capitalone.dashboard.repository.CustomRepositoryQuery;
import com.capitalone.dashboard.response.CodeReviewAuditResponseV2;
import com.capitalone.dashboard.util.GitHubParsedUrl;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class CodeReviewEvaluator extends Evaluator<CodeReviewAuditResponseV2> {

    private final CustomRepositoryQuery customRepositoryQuery;
    private final CodeReviewEvaluatorLegacy codeReviewEvaluatorLegacy;

     @Autowired
    public CodeReviewEvaluator(CustomRepositoryQuery customRepositoryQuery, CodeReviewEvaluatorLegacy codeReviewEvaluatorLegacy) {
        this.customRepositoryQuery = customRepositoryQuery;
         this.codeReviewEvaluatorLegacy = codeReviewEvaluatorLegacy;
     }




    @Override
    public Collection<CodeReviewAuditResponseV2> evaluate(Dashboard dashboard, long beginDate, long endDate, Collection data) throws AuditException {
        List<CodeReviewAuditResponseV2> responseV2s = new ArrayList<>();
        List<CollectorItem> repoItems = this.getCollectorItems(dashboard, "repo", CollectorType.SCM);
        if (CollectionUtils.isEmpty(repoItems)) {
            throw new AuditException("No code repository configured", AuditException.NO_COLLECTOR_ITEM_CONFIGURED);
        }

        Collection<CodeReviewAuditResponseV2> allReviews = new ArrayList<>();

        for (CollectorItem repoItem : repoItems) {
            String scmUrl = (String) repoItem.getOptions().get("url");
            String scmBranch = (String) repoItem.getOptions().get("branch");
            GitHubParsedUrl gitHubParsed = new GitHubParsedUrl(scmUrl);
            String parsedUrl = gitHubParsed.getUrl(); //making sure we have a goot url?

            CodeReviewAuditResponseV2 reviewResponse = evaluate(repoItem, beginDate, endDate, null);
            reviewResponse.setScmUrl(parsedUrl);
            reviewResponse.setScmBranch(scmBranch);
            reviewResponse.setLastUpdated(repoItem.getLastUpdated());
            responseV2s.add(reviewResponse);
        }
        return responseV2s;
    }

    @Override
    public CodeReviewAuditResponseV2 evaluate(CollectorItem collectorItem, long beginDate, long endDate, Collection data) throws AuditException {
        return getPeerReviewResponses(collectorItem,beginDate,endDate);
    }




    /**
     * Return an empty response in error situation
     *
     * @param repoItem
     * @param scmBranch
     * @param scmUrl
     * @return
     */
    private CodeReviewAuditResponseV2 getErrorResponse(CollectorItem repoItem, String scmBranch, String scmUrl) {
        CodeReviewAuditResponseV2 noPRsCodeReviewAuditResponse = new CodeReviewAuditResponseV2();
        noPRsCodeReviewAuditResponse.addAuditStatus(AuditStatus.COLLECTOR_ITEM_ERROR);

        noPRsCodeReviewAuditResponse.setLastUpdated(repoItem.getLastUpdated());
        noPRsCodeReviewAuditResponse.setScmBranch(scmBranch);
        noPRsCodeReviewAuditResponse.setScmUrl(scmUrl);
        noPRsCodeReviewAuditResponse.setErrorMessage(repoItem.getErrors() == null ? null : repoItem.getErrors().get(0).getErrorMessage());
        return noPRsCodeReviewAuditResponse;
    }

    public CodeReviewAuditResponseV2 getPeerReviewResponses(CollectorItem repoItem,
                                                            long beginDt, long endDt) {


        String scmUrl = (String) repoItem.getOptions().get("url");
        String scmBranch = (String) repoItem.getOptions().get("branch");

        GitHubParsedUrl gitHubParsed = new GitHubParsedUrl(scmUrl);

        String parsedUrl = gitHubParsed.getUrl(); //making sure we have a goot url?

        if (StringUtils.isEmpty(scmBranch) || StringUtils.isEmpty(scmUrl)) {
            return getErrorResponse(repoItem, scmBranch, parsedUrl);
        }

        if (!CollectionUtils.isEmpty(repoItem.getErrors())) {
            return getErrorResponse(repoItem, scmBranch, parsedUrl);

        }

        List<GitRequest> pullRequests = customRepositoryQuery.findByScmUrlIgnoreCaseAndScmBranchIgnoreCaseAndMergedAtGreaterThanEqualAndMergedAtLessThanEqual(scmUrl, scmBranch, beginDt, endDt);
        List<Commit> commits = customRepositoryQuery.findByScmUrlAndScmBranchAndScmCommitTimestampGreaterThanEqualAndScmCommitTimestampLessThanEqual(scmUrl, scmBranch, beginDt, endDt);
        CodeReviewAuditResponseV2 reviewAuditResponseV2 = new CodeReviewAuditResponseV2();

        if (CollectionUtils.isEmpty(pullRequests)) {
            reviewAuditResponseV2.addAuditStatus(AuditStatus.NO_PULL_REQ_FOR_DATE_RANGE);
        }

        reviewAuditResponseV2.setLastUpdated(repoItem.getLastUpdated());

        //                reviewAuditResponseV2.addPullRequest(pullRequestAudit);
        pullRequests.forEach(pr -> {
            String mergeSha = pr.getScmRevisionNumber();
            Optional<Commit> mergeOptionalCommit = commits.stream().filter(c -> Objects.equals(c.getScmRevisionNumber(), mergeSha)).findFirst();
            Commit mergeCommit = mergeOptionalCommit.orElse(null);
            if (mergeCommit == null) {
//                reviewAuditResponseV2.addPullRequest(pullRequestAudit);
                return;
            }
            CodeReviewAuditResponseV2.PullRequestAudit pullRequestAudit = new CodeReviewAuditResponseV2.PullRequestAudit();
            pullRequestAudit.setPullRequest(pr);
            List<Commit> commitsRelatedToPr = pr.getCommits();
            commitsRelatedToPr.sort(Comparator.comparing(e -> (e.getScmCommitTimestamp())));
            pullRequestAudit.addAuditStatus(pr.getUserId().equalsIgnoreCase(mergeCommit.getScmAuthorLogin()) ? AuditStatus.COMMITAUTHOR_EQ_MERGECOMMITER : AuditStatus.COMMITAUTHOR_NE_MERGECOMMITER);
            boolean peerReviewed = CommonCodeReview.computePeerReviewStatus(pr, settings, pullRequestAudit);
            pullRequestAudit.addAuditStatus(peerReviewed ? AuditStatus.PULLREQ_REVIEWED_BY_PEER : AuditStatus.PULLREQ_NOT_PEER_REVIEWED);
            String sourceRepo = pr.getSourceRepo();
            String targetRepo = pr.getTargetRepo();
            pullRequestAudit.addAuditStatus(sourceRepo == null ? AuditStatus.GIT_FORK_STRATEGY : sourceRepo.equalsIgnoreCase(targetRepo) ? AuditStatus.GIT_BRANCH_STRATEGY : AuditStatus.GIT_FORK_STRATEGY);
            reviewAuditResponseV2.addPullRequest(pullRequestAudit);
        });

        //check any commits not directly tied to pr
        commits.stream().filter(commit -> StringUtils.isEmpty(commit.getPullNumber()) && commit.getType() == CommitType.New).forEach(reviewAuditResponseV2::addDirectCommit);
        return reviewAuditResponseV2;
    }
}
