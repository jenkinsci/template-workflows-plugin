package org.jenkins.plugin.templateWorkflows;

import hudson.Extension;
import hudson.XmlFile;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.ViewJob;
import hudson.model.AbstractProject.AbstractProjectDescriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Queue.BuildableItem;
import hudson.model.Queue.Task;
import hudson.util.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.xml.transform.stream.StreamSource;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.bind.JavaScriptMethod;

public class TemplatesWorkflowJob extends ViewJob<TemplatesWorkflowJob,TemplateswWorkflowRun> implements TopLevelItem {

    private String templateName;
    private String templateInstanceName;
	private TemplateWorkflowInstances templateInstances;

    private static List<Job> relatedJobs;
    private static Map<String, String> jobParameters;

    public TemplatesWorkflowJob(ItemGroup itemGroup, String name) {
        super(itemGroup, name);
    }

    public String getTemplateName() {
        return templateName;
    }

    public String getTemplateInstanceName() {
		return templateInstanceName;
	}

    public Collection<TemplateWorkflowInstance> getTemplateInstances() {
        if (templateInstances == null) {
            return new ArrayList<TemplateWorkflowInstance>();
        }

        return templateInstances.values();
    }

    public String getProjectDesc() {
    	if (templateInstances == null || templateInstances.getInstances() == null || templateInstances.getInstances().size() == 0) {
    		return "This Project does not have any Associated Workflows";
    	}

    	return "This Project has " + templateInstances.getInstances().size() + " Assosiated Workflows";
    }

    public Set<String> getTemplateNames() {
        Set<String> ret = new LinkedHashSet<String>();

        List<Item> allItems = Jenkins.getInstance().getAllItems();
        for (Item i : allItems) {
            Collection<? extends Job> allJobs = i.getAllJobs();
            for (Job j : allJobs) {
                TemplateWorkflowProperty t = (TemplateWorkflowProperty)j.getProperty(TemplateWorkflowProperty.class);
                if (t != null && t.getTemplateName() != null) {
                	for (String tName : t.getTemplateName().split(",")) {
                		ret.add(tName.trim());
                	}
                }
            }
        }
        return ret;
    }

    @Override
    public Hudson getParent() {
        return Hudson.getInstance();
    }

    @Override
    public void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, FormException {
        templateName = req.getParameter("template.templateName");
        templateInstanceName = req.getParameter("template.templateInstanceName");
        String operation = req.getParameter("template.operation");
        boolean isNew = false;

        if (operation.equals("create")) {
        	isNew = true;
        }

        //Validate on server side
        for (Job j : relatedJobs) {
            if (StringUtils.isBlank(req.getParameter("template." + j.getName()))) {
                return;
            }
        }

        Map<String, String> replacementsParams = new HashMap<String, String>();
        for (String p : jobParameters.keySet()) {
            replacementsParams.put(p, req.getParameter("template." + p));
        }

        Map<String, String> replacementsJobs = new HashMap<String, String>();
        Map<String, Boolean> isNewJobMap = new HashMap<String, Boolean>();
        for (Job job : relatedJobs) {
            replacementsJobs.put(job.getName(), req.getParameter("template." + job.getName()));
        }

        for (Job job : relatedJobs) {
            String jobXml = FileUtils.readFileToString(job.getConfigFile().getFile());

            for (String origJob : replacementsJobs.keySet()) {
                jobXml = jobXml.replaceAll(">\\s*" + origJob + "\\s*</", ">" + replacementsJobs.get(origJob) + "</");
                jobXml = jobXml.replaceAll(",\\s*" + origJob, "," + replacementsJobs.get(origJob));
                jobXml = jobXml.replaceAll(origJob + "\\s*,",  replacementsJobs.get(origJob) + ",");
            }

            for (String key : replacementsParams.keySet()) {
                jobXml = jobXml.replaceAll("@@" + key + "@@", replacementsParams.get(key));
            }

            Boolean wasCreated = createOrUpdateJob(job.getName(), replacementsJobs.get(job.getName()), jobXml, isNew);
            isNewJobMap.put(replacementsJobs.get(job.getName()), wasCreated);
        }

        addTemplateInfo(templateInstanceName, replacementsParams, replacementsJobs, isNewJobMap);
        super.submit(req, rsp);
    }

    private Boolean createOrUpdateJob(String jobOrigName, String jobReplacedName, String jobXml, boolean isNew) throws IOException {
    	InputStream is = null;

    	try {
    		is = new ByteArrayInputStream(jobXml.getBytes("UTF-8"));

    		Job replacedJob = null;

    		if (isNew) {

    			//check if job already exist
    			Job job = (Job)Jenkins.getInstance().getItem(jobReplacedName);
    			if (job != null) {
    				return false;
    			}

    			replacedJob = (Job)Jenkins.getInstance().createProjectFromXML(jobReplacedName, is);
    			((Job)replacedJob).removeProperty(TemplateWorkflowProperty.class);
    			((Job)replacedJob).save();
    			return true;

    		} else {
    			replacedJob = (Job)Jenkins.getInstance().getItem(jobReplacedName);
    			replacedJob.updateByXml(new StreamSource(is));
    			replacedJob.removeProperty(TemplateWorkflowProperty.class);
    			replacedJob.save();
    			return null;
    		}

    	} finally {
    		try {
				is.close();
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
    	}
    }

    public TopLevelItemDescriptor getDescriptor() {
        return new DescriptorImpl();
    }

    @Extension
    public static final class DescriptorImpl extends AbstractProjectDescriptor {
        public String getDisplayName() {
            return "Template Workflow Job";
        }
        @Override
        public String getConfigPage() {
            return super.getConfigPage();
        }

        @Override
        public TopLevelItem newInstance(ItemGroup paramItemGroup, String paramString) {
            return new TemplatesWorkflowJob(Hudson.getInstance(), paramString);
        }
    }

    @JavaScriptMethod
    public JSONObject setTemplateInstanceName(String instanceName) {
    	this.templateInstanceName = instanceName;
    	 JSONObject ret = new JSONObject();
         ret.put("result", true);
         return ret;
    }

    @JavaScriptMethod
    public JSONObject executeWorkflow(String workflowName) throws IOException, InterruptedException {
    	boolean result = false;
    	String msg = "Starting Job/s not Defined for Workflow '" + workflowName + "'!";
    	String jobs = "";

    	try {
			TemplateWorkflowInstance templateInstance = templateInstances.get(workflowName);
			for (String jobTenplateName : templateInstance.getRelatedJobs().keySet()) {

				Job job = (Job)Jenkins.getInstance().getItem(jobTenplateName);
				TemplateWorkflowProperty t = (TemplateWorkflowProperty)job.getProperty(TemplateWorkflowProperty.class);
				if (t.getIsStartingWorkflowJob()) {
					String jobName = templateInstance.getRelatedJobs().get(jobTenplateName);
					job = (Job)Jenkins.getInstance().getItem(jobName);
					Jenkins.getInstance().getQueue().schedule((AbstractProject)job);
					jobs +=  ",'" + jobName + "' ";
					result = true;
				}
			}

		} catch (Exception e) {
			result = false;
	    	msg = "Error - Workflow '" + workflowName + "' was not Executed!";
		}

		if (result) {
			msg = jobs.replaceFirst(",", "") + "Scheduled";
		}

    	JSONObject ret = new JSONObject();
    	ret.put("result", result);
    	ret.put("msg", msg);
    	return ret;
    }


    @JavaScriptMethod
    public JSONObject deleteInstance(String instanceName) throws IOException, InterruptedException {

    	boolean result = true;
    	String msg = "";
    	TemplateWorkflowInstance templateInstance = templateInstances.get(instanceName);

    	for (String jobName : templateInstance.getRelatedJobs().values()) {

    		Job job = (Job)Jenkins.getInstance().getItem(jobName);
    		if (job != null && job.isBuilding()) {
				result = false;
				msg = "Job " + job.getName() + " is Currently Building";
				break;
			} else if (job != null && job.isInQueue()) {
				result = false;
				msg = "Job " + job.getName() + " is in the Build Queue";
				break;
			}

    	}

    	if (result) {
    		try {
				for (String jobName : templateInstance.getRelatedJobs().values()) {

					if (!templateInstance.isJobWasCreateByWorkflow(jobName)) {
		    			continue;
		    		}

					Job job = (Job)Jenkins.getInstance().getItem(jobName);
					if (job != null) {
						job.delete();
					}
				}
				templateInstances.remove(instanceName);
				save();
			} catch (Exception e) {
				result = false;
				msg = "Failed to Delete " + instanceName + ", Please Delete it Manually";
			}
    	}

    	JSONObject ret = new JSONObject();
    	ret.put("result", result);
    	ret.put("msg", msg);
    	return ret;
    }

    @JavaScriptMethod
    public JSONObject validateJobName(String newJobName, boolean allowUseOfExistingJob) {

        String cssClass = "info";
        String msg = "Valid name";
        boolean result = true;

        if (StringUtils.isBlank(newJobName)) {
        	cssClass = "error";
            msg= "Job name can't be empty!";
            result = false;
        }

        List<Item> allItems = Jenkins.getInstance().getAllItems();
        for (Item i : allItems) {
        	Collection<? extends Job> allJobs = i.getAllJobs();
        	for (Job j : allJobs) {
        		if (j.getName().equalsIgnoreCase(newJobName)) {
        			if (allowUseOfExistingJob) {

        				TemplateWorkflowProperty t = (TemplateWorkflowProperty)j.getProperty(TemplateWorkflowProperty.class);
        				if (t == null) {
        					cssClass = "warning";
        					msg = "Using existing job defenition";
        				} else {
        					cssClass = "error";
            				msg = "You can't use a job that is a bulding block for a template workflow";
            				result = false;
        				}

        			} else {
        				cssClass = "error";
        				msg = "Job already defined with name: '" + newJobName + "'";
        				result = false;
        			}

        		}
        	}
        }

        JSONObject ret = new JSONObject();
        ret.put("cssClass", cssClass);
        ret.put("msg", msg);
        ret.put("result", result);
        return ret;
    }


    @JavaScriptMethod
    public JSONObject validateJobIsNotRunning(String jobName) {

    	String msg = "";
    	boolean result = true;
    	AbstractProject job = (AbstractProject)Jenkins.getInstance().getItem(jobName);

    	if (job != null && job.isBuilding()) {
			result = false;
			msg = "Job " + job.getName() + " is Currently Building";

    	} else if (job != null && job.isInQueue()) {
    		result = false;
			msg = "Job " + job.getName() + " is in the Build Queue";
		}

        JSONObject ret = new JSONObject();
        ret.put("result", result);
        ret.put("msg", msg);
        return ret;
    }


    @JavaScriptMethod
    public JSONObject validateTemplateName(String instanceNewName) {

        boolean result = true;
        String msg = "Valid name";

        if (StringUtils.isBlank(instanceNewName)) {
            result = false;
            msg= "Workflow name can't be empty!";
        }

        if (templateInstances != null) {
        	for (String instanceName : templateInstances.keySet()) {
        		if (instanceName.equalsIgnoreCase(instanceNewName)) {
        			result = false;
        			msg = "Workflow already defined with name: '" + instanceNewName + "'";
        		}
        	}
        }

        JSONObject ret = new JSONObject();
        ret.put("result", result);
        ret.put("msg", msg);
        return ret;
    }

    @JavaScriptMethod
    public JSONObject refresh(String templateName) {

    	 JSONObject ret = new JSONObject();

    	//on create
    	if (templateInstanceName == null) {
    		 ret.put("result", true);
    	     ret.put("msg", "<div>Click the 'Create Workflow' Link to define workflows</div>");
    		 return ret;
    	//after delete
    	} else if (!templateInstanceName.equals("template.createNewTemplate")) {
    		if (templateInstances.get(templateInstanceName) == null) {
    			ret.put("result", true);
    			ret.put("msg", "<div>Click the 'Create Workflow' Link to Define Workflows</div>");
    			return ret;
    		}
    	}

    	boolean isNew = templateInstanceName.equals("template.createNewTemplate") ? true : false;
    	TemplateWorkflowInstance templateInstance = null;

        try {
        	if (isNew) {
        		relatedJobs = getRelatedJobs(templateName);
        		jobParameters = getTemplateParamaters(relatedJobs);
        	} else {
        		templateInstance = templateInstances.get(templateInstanceName);
        		relatedJobs = getRelatedJobs(templateInstance.getTemplateName());
        		jobParameters = templateInstance.getJobParameters();
        	}
            StringBuilder build = new StringBuilder();
            build.append("<div>&nbsp;</div>");

            if (isNew) {
            	build.append("<div style=\"font-weight:bold;\">Please Select a Workflow Name: </div>");
	            build.append("<input name=\"template.templateInstanceName\" ").append(
	                                 "id=\"template.templateInstanceName\"  ").append(
	                                 "onChange=\"validateTemplateName()\"").append(
	                                 "onkeydown=\"validateTemplateName()\"").append(
	                                 "onkeyup=\"validateTemplateName()\"").append(
	                                 "class=\"setting-input\" value=\"\" type=\"text\"/>");
	            build.append("<tr><td></td><td><div id =\"template.templateInstanceName.validation\" style=\"visibility: hidden;\"></div></td></tr>");
            } else {
            	build.append("<div>");
            	build.append("<span style=\"font-weight:bold;\">Workflow Name: '").append(templateInstanceName).append("'</span>");
            	build.append("<span> (Created From Template: '").append(templateInstance.getTemplateName()).append("')</span>");
            	build.append("</div>");
            	build.append("<input type=\"hidden\" id=\"template.templateInstanceName\" name=\"template.templateInstanceName\" value=\"" + templateInstanceName + "\">");
            }

            build.append("<div>&nbsp;</div>");
            build.append("<div style=\"font-weight:bold;\">Workflow is Defined out of ").append(relatedJobs.size()).append(" Jobs:</div>");
            build.append("<table border=\"0\" cellpadding=\"1\" cellspacing=\"1\">");
            for (Job j : relatedJobs) {
            	if (isNew) {
	                build.append( "<tr>").append(
	                           "<td>").append(j.getName()).append(":&nbsp;</td>").append(
	                             "<td style=\"width:300px;\">").append(
	                             "<input name=\"template.").append(j.getName()).append("\" ").append(
	                                     "id=\"template.").append(j.getName()).append("\"  ").append(
	                                     "onChange=\"validateJobName('").append(j.getName()).append("', document.getElementById('template.").append(j.getName()).append("').value)\"").append(
	                                     "onkeydown=\"validateJobName('").append(j.getName()).append("', document.getElementById('template.").append(j.getName()).append("').value)\"").append(
	                                     "onkeyup=\"validateJobName('").append(j.getName()).append("', document.getElementById('template.").append(j.getName()).append("').value)\"").append(
	                                     "class=\"setting-input\" value=\"\" type=\"text\"/>").append(
	                             "</td>").append(
	                         "</tr>");
	                build.append("<tr><td></td><td><div id =\"").append(j.getName()).append(".validation\" style=\"visibility: hidden;\"></div></td></tr>");
            	} else {
            		 String jobReplacedName = templateInstance.getRelatedJobs().get(j.getName());
            		 String href = Jenkins.getInstance().getRootUrl() + "job/" + jobReplacedName;
            		 build.append("<tr>").append(
	                              "<td>").append(j.getName()).append(":&nbsp;</td>").append(
	                              "<td style=\"width:300px;\">").append(
	                              "<a class=\"tip\" id=\"").append("template_job.").append(j.getName()).append("\" href=\"").append(href).append("\">").append(jobReplacedName).append("</a>").append(
	                              "<input type=\"hidden\" id=\"template.").append(j.getName()).append("\" name=\"template.").append(j.getName()).append("\" value=\"").append(jobReplacedName).append("\">").append(
	                              "</td>").append(
	                              "</tr>");
            	}
            }

            build.append("</table>");
            if (isNew) {
            	build.append("<div><input type=\"checkbox\" onchange=\"return validateAllNames();\" id=\"allow_exist_name\" name=\"allow_exist_name\"/>Allow the Use of Existing Jobs</div>");
            }

            build.append("<div>&nbsp;</div>");
            build.append("<div>&nbsp;</div>");


            build.append("<div style=\"font-weight:bold;\">Workflow Parameters:</div>");
            build.append("<table border=\"0\" cellpadding=\"1\" cellspacing=\"1\">");
            for (String p : jobParameters.keySet()) {
            	String value = jobParameters.get(p) != null ? jobParameters.get(p) : "";
                build.append("<tr><td>"+p+":&nbsp;</td><td style=\"width:300px;\"><input name=\"template.").append(p).append("\"  class=\"setting-input\" value=\"").append(value).append("\" type=\"text\"/></td></tr>");
            }
            build.append("</table>");
            build.append("<div>&nbsp;</div>");

            if (isNew) {
            	build.append("<input type=\"hidden\" name=\"template.operation\" value=\"create\">");
            	build.append("<input class=\"yui-button,yui-submit-button\" onclick=\"return validateCreate();\" type=\"submit\"  value=\"Create\">");
            } else {
            	build.append("<input type=\"hidden\" name=\"template.operation\" value=\"update\">");
            	build.append("<input class=\"yui-button,yui-submit-button\" onclick=\"return validateUpdate();\" type=\"submit\" name=\"template.operation\" value=\"Update\">");
            }

            ret.put("result", true);
            ret.put("msg", build.toString());
            return ret;

        } catch (Exception e) {
        	e.printStackTrace();
        	ret.put("result", false);
            ret.put("msg", "<div>Opps.. an Error Occur (" + e.getMessage() + ")</div>");
            return ret;
        }
    }

    private List<Job> getRelatedJobs(String templateName) {
    	List<Job> relatedJobs = new ArrayList<Job>();
        List<Item> allItems = Jenkins.getInstance().getAllItems();
        for (Item i : allItems) {
            Collection<? extends Job> allJobs = i.getAllJobs();
            for (Job j : allJobs) {
                TemplateWorkflowProperty t = (TemplateWorkflowProperty)j.getProperty(TemplateWorkflowProperty.class);
                if (t != null) {
                	for (String tName : t.getTemplateName().split(",")) {
                		if (templateName.equalsIgnoreCase(tName.trim())) {
                			relatedJobs.add(j);
                		}
                	}
                }
            }
        }

        return relatedJobs;
    }

    private Map<String, String> getTemplateParamaters(List<Job> relatedJobs) throws IOException {
    	Pattern pattern = Pattern.compile("@@(.*?)@@");

    	Map<String, String> jobParameters = new HashMap<String, String>();
        for (Job job : relatedJobs) {
            List<String> lines = FileUtils.readLines(job.getConfigFile().getFile());
            for (String line : lines) {
                Matcher matcher = pattern.matcher(line);
                while(matcher.find()) {
                    jobParameters.put(matcher.group(1), null);
                }
            }
        }

        return jobParameters;
    }

    private void addTemplateInfo(String instanceName,
    		                     Map<String, String> replacementsParams,
    		                     Map<String, String> replacementsJobs,
    		                     Map<String, Boolean> isNewJobMap) throws IOException, ServletException {

    	if (templateInstances == null) {
    		templateInstances = new TemplateWorkflowInstances();
    	}

    	TemplateWorkflowInstance instance = templateInstances.get(instanceName);
    	if (instance == null) {
    		instance = new TemplateWorkflowInstance(templateName, instanceName, isNewJobMap);
    	}

    	instance.setJobParameters(replacementsParams);
    	instance.setRelatedJobs(replacementsJobs);
    	templateInstances.put(instanceName, instance);

    	this.addProperty(templateInstances);
    	this.save();
    }

    @Override
    protected void reload() {
    };
}
