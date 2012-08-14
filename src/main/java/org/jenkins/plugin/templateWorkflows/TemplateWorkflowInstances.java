package org.jenkins.plugin.templateWorkflows;

import hudson.Extension;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;


@ExportedBean
public class TemplateWorkflowInstances extends JobProperty<TemplatesWorkflowJob> {

	private Map<String, TemplateWorkflowInstance> instances;

	@Exported
	public Map<String, TemplateWorkflowInstance> getInstances() {
		return instances;
	}

	@DataBoundConstructor
	public TemplateWorkflowInstances(Map<String, TemplateWorkflowInstance> instances) {
		this.instances = instances;
	}

	public TemplateWorkflowInstances() {
		instances = new HashMap<String, TemplateWorkflowInstance>();
	}

	public TemplateWorkflowInstance get(String instanceName) {
		return instances.get(instanceName);
	}

	public void put(String instanceName, TemplateWorkflowInstance instance) {
		instances.put(instanceName, instance);
	}

	public void remove(String instanceName) {
		instances.remove(instanceName);
	}

	public Set<String> keySet() {
		return instances.keySet();
	}

	public Collection<TemplateWorkflowInstance> values() {
		return instances.values();
	}

	@Extension
    public static class DescriptorImpl extends JobPropertyDescriptor {
        @Override
        public String getDisplayName() {
         return "TemplateInstances";
        }

        @Override
        public TemplateWorkflowInstances newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return formData.has("com.exlibris.template.TemplateInstances")
            ? req.bindJSON(TemplateWorkflowInstances.class, formData.getJSONObject("com.exlibris.template.TemplateInstances"))
            : null;
        }
    }
}
