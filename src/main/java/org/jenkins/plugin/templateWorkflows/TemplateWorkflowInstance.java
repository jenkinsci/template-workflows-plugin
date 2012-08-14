package org.jenkins.plugin.templateWorkflows;

import java.util.HashMap;
import java.util.Map;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public class TemplateWorkflowInstance {

	//mark if the job was create during the template definition - for delete logic
	private Map<String, Boolean> isNewJobMap;
	private String instanceName;
	private String templateName;
	private Map<String, String> jobParameters;
	private Map<String, String> relatedJobs;

	public TemplateWorkflowInstance(String templateName,
			                        String instanceName,
			                        Map<String, Boolean> isNewJobMap) {
		this.templateName = templateName;
		this.instanceName = instanceName;
		this.isNewJobMap = isNewJobMap;
		jobParameters = new HashMap<String, String>();
		relatedJobs = new HashMap<String, String>();
	}

	@Exported
	public String getInstanceName() {
		return instanceName;
	}

	@Exported
	public String getTemplateName() {
		return templateName;
	}

	@Exported
	public Map<String, String> getRelatedJobs() {
		return relatedJobs;
	}

	@Exported
	public Map<String, String> getJobParameters() {
		return jobParameters;
	}

	@Exported
	public boolean isJobWasCreateByWorkflow(String jobName) {
		return isNewJobMap.get(jobName);
	}

	public void setJobParameters(Map<String, String> jobParameters) {
		this.jobParameters = jobParameters;
	}

	public void setRelatedJobs(Map<String, String> relatedJobs) {
		this.relatedJobs = relatedJobs;
	}
}
