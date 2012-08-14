package org.jenkins.plugin.templateWorkflows;

import hudson.model.Run;

import java.io.File;
import java.io.IOException;

public class TemplateswWorkflowRun extends Run<TemplatesWorkflowJob,TemplateswWorkflowRun> {

    public TemplateswWorkflowRun(TemplatesWorkflowJob project) throws IOException {
        super(project);
    }

    public TemplateswWorkflowRun(TemplatesWorkflowJob project, File buildDir) throws IOException {
        super(project, buildDir);
    }

}
