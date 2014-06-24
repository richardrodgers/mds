/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.info.domain;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * WorkflowEntity contains basic aggregate information about
 * current repository workflow. Currently,
 * reports only counts of workspace and workflow items. 
 *
 * @author richardrodgers
 */

@XmlRootElement(name="workflow")
public class WorkflowEntity {

    private long workspace;
    private long workflow;
    
    public WorkflowEntity() {}

    public WorkflowEntity(long workspace, long workflow) {
        this.workspace = workspace;
        this.workflow = workflow;
    }

    public long getWorkspace() {
        return workspace;
    }

    public void setWorkspace(long workspace) {
         this.workspace = workspace;
    }

    public long getWorkflow() {
        return workflow;
    }

    public void setWorkflow(long workflow) {
         this.workflow = workflow;
    }
}
