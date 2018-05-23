package com.capitalone.dashboard.evaluator;

import com.capitalone.dashboard.model.AuditException;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.CollectorType;
import com.capitalone.dashboard.model.Dashboard;
import com.capitalone.dashboard.model.DashboardType;
import com.capitalone.dashboard.model.TestResult;
import com.capitalone.dashboard.model.TestSuiteType;
import com.capitalone.dashboard.model.TestCapability;
import com.capitalone.dashboard.model.TestSuite;
import com.capitalone.dashboard.model.TestCase;
import com.capitalone.dashboard.model.StoryIndicator;
import com.capitalone.dashboard.model.Feature;
import com.capitalone.dashboard.repository.FeatureRepository;
import com.capitalone.dashboard.repository.TestResultRepository;
import com.capitalone.dashboard.response.TestResultsAuditResponse;
import com.capitalone.dashboard.status.TestResultAuditStatus;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.text.ParseException;
import java.text.SimpleDateFormat;


@Component
public class RegressionTestResultEvaluator extends Evaluator<TestResultsAuditResponse> {

    private final TestResultRepository testResultRepository;
    private final FeatureRepository featureRepository;

    @Autowired
    public RegressionTestResultEvaluator(TestResultRepository testResultRepository, FeatureRepository featureRepository) {
        this.testResultRepository = testResultRepository;
        this.featureRepository = featureRepository;
    }

    @Override
    public Collection<TestResultsAuditResponse> evaluate(Dashboard dashboard, long beginDate, long endDate, Map<?, ?> dummy) throws AuditException {
        List<CollectorItem> testItems = getCollectorItems(dashboard, "codeanalysis", CollectorType.Test);

        Collection<TestResultsAuditResponse> testResultsAuditResponse = new ArrayList<>();

        if (CollectionUtils.isEmpty(testItems)) {
            throw new AuditException("No tests configured", AuditException.NO_COLLECTOR_ITEM_CONFIGURED);
        }

        testItems.forEach(testItem -> {
            testResultsAuditResponse.add(getRegressionTestResultAudit(dashboard, testItem, beginDate, endDate));
        });

        return testResultsAuditResponse;
    }

    @Override
    public TestResultsAuditResponse evaluate(CollectorItem collectorItem, long beginDate, long endDate, Map<?, ?> dummy) {
        return null;
    }

    /**
     * Gets the json response from test_results collection with story information based on tags.
     *
     * @param dashboard
     * @param testItem
     * @param beginDate
     * @param endDate
     * @return
     */
    private TestResultsAuditResponse getRegressionTestResultAudit(Dashboard dashboard, CollectorItem testItem, long beginDate, long endDate) {

        List<TestResult> testResults = testResultRepository
                .findByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(testItem.getId(), beginDate-1, endDate+1);

        TestResultsAuditResponse testResultsAuditResponse = new TestResultsAuditResponse();
        int traceabilityThreshold = settings.getThreshold();
        List<StoryIndicator> totalStoryIndicatorList = new ArrayList<>();

        for (TestResult testResult : testResults) {
            if (TestSuiteType.Regression.toString().equalsIgnoreCase(testResult.getType().name()) ||
                    TestSuiteType.Functional.toString().equalsIgnoreCase(testResult.getType().name())) {
                testResultsAuditResponse.addAuditStatus((testResult.getFailureCount() == 0) ? TestResultAuditStatus.TEST_RESULT_AUDIT_OK : TestResultAuditStatus.TEST_RESULT_AUDIT_FAIL);
                testResultsAuditResponse.setTestCapabilities(testResult.getTestCapabilities());
                testResultsAuditResponse.setLastExecutionTime(testResult.getStartTime());
                List<String> totalStories = this.getTotalCompletedStoriesInGivenDateRange(dashboard.getTitle(), beginDate, endDate);
                testResultsAuditResponse.setTotalStories(totalStories);
                testResultsAuditResponse.setTotalStoryCount(totalStories.size());
                testResultsAuditResponse.setThreshold(traceabilityThreshold);
                testResults.stream()
                        .map(TestResult::getTestCapabilities).flatMap(Collection::stream)
                        .map(TestCapability::getTestSuites).flatMap(Collection::stream)
                        .map(TestSuite::getTestCases).flatMap(Collection::stream)
                        .forEach(testCase -> {
                            List<StoryIndicator> storyIndicatorList = this.getStoryIndicatorsInGivenDateRange(dashboard, testResultsAuditResponse, testCase, beginDate, endDate);
                            testCase.setStoryIndicators(storyIndicatorList);
                            if (CollectionUtils.isEmpty(storyIndicatorList)) {
                                testResultsAuditResponse.addAuditStatus(TestResultAuditStatus.TEST_RESULT_AUDIT_MISSING);
                            }
                            storyIndicatorList.forEach(sil -> {
                                if (!totalStoryIndicatorList.stream().anyMatch(o -> o.getStoryNumber().equals(sil.getStoryNumber()))) {
                                    totalStoryIndicatorList.add(sil);
                                }
                            });
                        });

                if (totalStories.size() > 0) {
                    int percentTraceability = (totalStoryIndicatorList.size() * 100) / totalStories.size();
                    testResultsAuditResponse.setPercentTraceability(percentTraceability);
                    if (traceabilityThreshold == 0) {
                        testResultsAuditResponse.addAuditStatus(TestResultAuditStatus.TEST_RESULTS_TRACEABILITY_THRESHOLD_DEFAULT);
                    }
                } else {
                    testResultsAuditResponse.addAuditStatus(TestResultAuditStatus.TEST_RESULTS_TRACEABILITY_NOT_FOUND_IN_GIVEN_DATE_RANGE);
                }
                break;
            }
        }

        return testResultsAuditResponse;
    }

    /**
     * Gets list of Stories & Story details based on the cucumber tags in `REGEX_ANY_STRING_MATCHING_FEATURE_ID` Regex Pattern
     *
     * @param dashboard
     * @param testResultsAuditResponse
     * @param testCase
     * @param beginDate
     * @param endDate
     * @return
     */
    private List<StoryIndicator> getStoryIndicatorsInGivenDateRange(Dashboard dashboard, TestResultsAuditResponse testResultsAuditResponse, TestCase testCase, Long beginDate, Long endDate) {

        final String REGEX_ANY_STRING_MATCHING_FEATURE_ID = settings.getFeatureIDPattern();
        List<StoryIndicator> storyIndicatorList = new ArrayList<>();
        Set<String> tags = testCase.getTags();

        if (CollectionUtils.isEmpty(tags)) {
            testResultsAuditResponse.addAuditStatus(TestResultAuditStatus.TEST_RESULTS_TRACEABILITY_NOT_FOUND);
        } else {
            Pattern p = Pattern.compile(REGEX_ANY_STRING_MATCHING_FEATURE_ID);
            tags.forEach(tag -> {
                Matcher tagMatch = p.matcher(tag);
                if (tagMatch.find()) {
                    List<Feature> featureDetails = featureRepository.getStoryByNumber(tag.substring(1, tag.length()));
                    if (!this.getTotalStoriesInGivenDateRange(dashboard.getTitle(), beginDate, endDate).contains(tag.substring(1, tag.length()))) {
                        testResultsAuditResponse.addAuditStatus(TestResultAuditStatus.TEST_RESULTS_TRACEABILITY_STORY_NOT_FOUND);
                    }
                    featureDetails.stream()
                            .forEach(feature -> {
                                if (this.getEpochChangeDate(feature) >= beginDate && this.getEpochChangeDate(feature) <= endDate) {
                                    if (feature.getsStatus().equalsIgnoreCase("ACCEPTED") ||
                                            feature.getsStatus().equalsIgnoreCase("DONE") ||
                                            feature.getsStatus().equalsIgnoreCase("RESOLVED") ||
                                            feature.getsState().equalsIgnoreCase("CLOSED")) {

                                        testResultsAuditResponse.addAuditStatus(TestResultAuditStatus.TEST_RESULTS_TRACEABILITY_STORY_MATCH);

                                        StoryIndicator storyIndicator = new StoryIndicator();

                                        storyIndicator.setStoryId(feature.getsId());
                                        storyIndicator.setStoryType(feature.getsTypeName());
                                        storyIndicator.setStoryNumber(feature.getsNumber());
                                        storyIndicator.setStoryName(feature.getsName());
                                        storyIndicator.setEpicNumber(feature.getsEpicNumber());
                                        storyIndicator.setEpicName(feature.getsEpicName());
                                        storyIndicator.setProjectName(feature.getsProjectName());
                                        storyIndicator.setTeamName(feature.getsTeamName());
                                        storyIndicator.setSprintName(feature.getsSprintName());
                                        storyIndicator.setStoryStatus(feature.getsStatus());
                                        storyIndicator.setStoryState(feature.getsState());
                                        storyIndicatorList.add(storyIndicator);
                                    } else {
                                        testResultsAuditResponse.addAuditStatus(TestResultAuditStatus.TEST_RESULTS_TRACEABILITY_STORY_STATUS_INVALID);
                                    }
                                }
                            });
                }
            });
        }

        return storyIndicatorList;
    }

    /**
     * Gets total completed stories(ACCEPTED/DONE/RESOLVED/CLOSED) based on the change date
     *
     * @param dashboard
     * @param beginDate
     * @param endDate
     * @return
     */
    private List<String> getTotalCompletedStoriesInGivenDateRange(String dashboard, Long beginDate, Long endDate) {

        List<String> totalStories = new ArrayList<>();
        Dashboard dashboardDetails = dashboardRepository.findByTitleAndType(dashboard, DashboardType.Team);

        dashboardDetails.getWidgets().forEach(widget ->
        {
            if (widget.getName().equals("feature")) {
                List<Feature> featureDetails = featureRepository.getStoryByTeamID(widget.getOptions().get("teamId").toString());
                featureDetails.stream()
                        .forEach(feature -> {
                            if (feature.getsStatus().equalsIgnoreCase("ACCEPTED") ||
                                    feature.getsStatus().equalsIgnoreCase("DONE") ||
                                    feature.getsStatus().equalsIgnoreCase("RESOLVED") ||
                                    feature.getsState().equalsIgnoreCase("CLOSED")) {
                                if (this.getEpochChangeDate(feature) >= beginDate && this.getEpochChangeDate(feature) <= endDate) {
                                    totalStories.add(feature.getsNumber());
                                }
                            }
                        });
            }
        });

        return totalStories;
    }

    /**
     * Gets total stories(ALL STATUS) based on the change date
     *
     * @param dashboard
     * @param beginDate
     * @param endDate
     * @return
     */
    private List<String> getTotalStoriesInGivenDateRange(String dashboard, Long beginDate, Long endDate) {

        List<String> totalStories = new ArrayList<>();
        Dashboard dashboardDetails = dashboardRepository.findByTitleAndType(dashboard, DashboardType.Team);

        dashboardDetails.getWidgets().forEach(widget ->
        {
            if (widget.getName().equals("feature")) {
                List<Feature> featureDetails = featureRepository.getStoryByTeamID(widget.getOptions().get("teamId").toString());
                featureDetails.stream()
                        .forEach(feature -> {
                            if (this.getEpochChangeDate(feature) >= beginDate && this.getEpochChangeDate(feature) <= endDate) {
                                totalStories.add(feature.getsNumber());
                            }
                        });
            }
        });

        return totalStories;
    }

    /**
     * Coverts the Human readable time date to Epoch Time Stamp in Milliseconds
     *
     * @param feature
     * @return
     */
    private long getEpochChangeDate(Feature feature) {
        String datePattern = "yyyy-MM-dd'T'HH:mm:ss.SSS";
        long changeDate = 0;

        try {
            SimpleDateFormat sdf = new SimpleDateFormat(datePattern);
            Date dt = sdf.parse(feature.getChangeDate());
            changeDate = dt.getTime();
        } catch(ParseException e) {
            e.printStackTrace();
        }

        return changeDate;
    }

}
