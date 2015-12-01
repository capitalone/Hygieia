package com.capitalone.dashboard.collector;

import com.capitalone.dashboard.model.Build;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.CollectorType;
import com.capitalone.dashboard.model.JenkinsCucumberTestCollector;
import com.capitalone.dashboard.model.JenkinsJob;
import com.capitalone.dashboard.model.TestResult;
import com.capitalone.dashboard.repository.BaseCollectorRepository;
import com.capitalone.dashboard.repository.ComponentRepository;
import com.capitalone.dashboard.repository.JenkinsCucumberTestCollectorRepository;
import com.capitalone.dashboard.repository.JenkinsCucumberTestJobRepository;
import com.capitalone.dashboard.repository.TestResultRepository;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by Kyle Heide on 2/12/15.
 */
@Component
public class JenkinsCucumberTestCollectorTask extends
		CollectorTask<JenkinsCucumberTestCollector> {

	@SuppressWarnings("unused")
	private static final Logger LOG = LoggerFactory.getLogger(JenkinsCucumberTestCollector.class);
	private static final int CLEANUP_INTERVAL = 3600000;

	private final JenkinsCucumberTestCollectorRepository jenkinsCucumberTestCollectorRepository;
	private final JenkinsCucumberTestJobRepository jenkinsCucumberTestJobRepository;
	private final TestResultRepository testResultRepository;
	private final JenkinsClient jenkinsClient;
	private final JenkinsSettings jenkinsCucumberTestSettings;
	private final ComponentRepository dbComponentRepository;


	@Autowired
	public JenkinsCucumberTestCollectorTask(
			TaskScheduler taskScheduler,
			JenkinsCucumberTestCollectorRepository jenkinsCucumberTestCollectorRepository,
			JenkinsCucumberTestJobRepository jenkinsCucumberTestJobRepository,
			TestResultRepository testResultRepository,
			JenkinsClient jenkinsCucumberTestClient,
			JenkinsSettings jenkinsCucumberTestSettings,
			ComponentRepository dbComponentRepository) {
		super(taskScheduler, "JenkinsCucumberTest");
		this.jenkinsCucumberTestCollectorRepository = jenkinsCucumberTestCollectorRepository;
		this.jenkinsCucumberTestJobRepository = jenkinsCucumberTestJobRepository;
		this.testResultRepository = testResultRepository;
		this.jenkinsClient = jenkinsCucumberTestClient;
		this.jenkinsCucumberTestSettings = jenkinsCucumberTestSettings;
		this.dbComponentRepository = dbComponentRepository;
	}

	@Override
	public JenkinsCucumberTestCollector getCollector() {
		return JenkinsCucumberTestCollector
				.prototype(jenkinsCucumberTestSettings.getServers());
	}

	@Override
	public BaseCollectorRepository<JenkinsCucumberTestCollector> getCollectorRepository() {
		return jenkinsCucumberTestCollectorRepository;
	}

	@Override
	public String getCron() {
		return jenkinsCucumberTestSettings.getCron();
	}

	@Override
	public void collect(JenkinsCucumberTestCollector collector) {

		long start = System.currentTimeMillis();

		// Clean up every hour
		if ((start - collector.getLastExecuted()) > CLEANUP_INTERVAL) {
			clean(collector);
		}

		for (String instanceUrl : collector.getBuildServers()) {
			logBanner(instanceUrl);

			Map<JenkinsJob, Set<Build>> buildsByJob = jenkinsClient
					.getInstanceJobs(instanceUrl);
			log("Fetched jobs", start);

			addNewJobs(buildsByJob.keySet(), collector);

			addNewTestSuites(enabledJobs(collector, instanceUrl), buildsByJob);

			log("Finished", start);
		}

	}

	/**
	 * Clean up unused hudson/jenkins collector items
	 *
	 * @param collector
	 */
	@SuppressWarnings("PMD.AvoidDeeplyNestedIfStmts")
	private void clean(JenkinsCucumberTestCollector collector) {
		Set<ObjectId> uniqueIDs = new HashSet<>();
		for (com.capitalone.dashboard.model.Component comp : dbComponentRepository
				.findAll()) {
			if (comp.getCollectorItems() != null && !comp.getCollectorItems().isEmpty()) {
				List<CollectorItem> itemList = comp.getCollectorItems().get(
						CollectorType.Test);
				if (itemList != null) {
					for (CollectorItem ci : itemList) {
						if (ci != null && ci.getCollectorId().equals(collector.getId())) {
							uniqueIDs.add(ci.getId());
						}
					}
				}
			}
		}
		List<JenkinsJob> jobList = new ArrayList<>();
		Set<ObjectId> udId = new HashSet<>();
		udId.add(collector.getId());
		for (JenkinsJob job : jenkinsCucumberTestJobRepository
				.findByCollectorIdIn(udId)) {
			if (job != null) {
				job.setEnabled(uniqueIDs.contains(job.getId()));
				jobList.add(job);
			}
		}
		jenkinsCucumberTestJobRepository.save(jobList);
	}

	// Jenkins Helper methods

	private List<JenkinsJob> enabledJobs(
			JenkinsCucumberTestCollector collector, String instanceUrl) {
		return jenkinsCucumberTestJobRepository.findEnabledJenkinsJobs(
				collector.getId(), instanceUrl);
	}

	/**
	 * Adds new {@link JenkinsJob}s to the database as disabled jobs.
	 *
	 * @param jobs
	 *            list of {@link JenkinsJob}s
	 * @param collector
	 *            the {@link JenkinsCucumberTestCollector}
	 */
	private void addNewJobs(Set<JenkinsJob> jobs,
			JenkinsCucumberTestCollector collector) {
		long start = System.currentTimeMillis();
		int count = 0;

		for (JenkinsJob job : jobs) {

			if (jenkinsClient.buildHasCucumberResults(job.getJobUrl())
					&& isNewJob(collector, job)) {
				job.setCollectorId(collector.getId());
				job.setEnabled(false); // Do not enable for collection. Will be
										// enabled when added to dashboard
				job.setDescription(job.getJobName());
				jenkinsCucumberTestJobRepository.save(job);
				count++;
			}

		}
		log("New jobs", start, count);
	}

	private void addNewTestSuites(List<JenkinsJob> enabledJobs,
			Map<JenkinsJob, Set<Build>> buildsByJob) {
		long start = System.currentTimeMillis();
		int count = 0;

		for (JenkinsJob job : enabledJobs) {

			for (Build buildSummary : nullSafe(buildsByJob.get(job))) {

				if (jenkinsClient.buildHasCucumberResults(buildSummary
						.getBuildUrl())
						&& isNewCucumberResult(job, buildSummary)) {

					// Obtain the Test Result
					TestResult result = jenkinsClient
							.getCucumberTestResult(buildSummary.getBuildUrl());
					if (result != null) {
						result.setCollectorItemId(job.getId());
						result.setTimestamp(System.currentTimeMillis());
						testResultRepository.save(result);
						count++;
					}
				}
			}
		}
		log("New test suites", start, count);
	}

	private boolean isNewJob(JenkinsCucumberTestCollector collector,
			JenkinsJob job) {
		return jenkinsCucumberTestJobRepository.findJenkinsJob(
				collector.getId(), job.getInstanceUrl(), job.getJobName()) == null;
	}

	private boolean isNewCucumberResult(JenkinsJob job, Build build) {
		return testResultRepository.findByCollectorItemIdAndExecutionId(
				job.getId(), build.getNumber()) == null;
	}

	private Set<Build> nullSafe(Set<Build> builds) {
		return builds == null ? new HashSet<Build>() : builds;
	}
}
