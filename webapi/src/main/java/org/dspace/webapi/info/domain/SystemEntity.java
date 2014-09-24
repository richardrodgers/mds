/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.info.domain;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

/**
 * SystemEntity describes the software configuration of the interrogated system.
 * It lists installed modules, reporting for each the Maven coordinates and installation date.
 *
 * @author richardrodgers
 */

@XmlRootElement(name="system")
public class SystemEntity extends InfoEntity {

    @XmlElementWrapper(name="modules")
    @XmlElement(name="module")
    private List<Module> modules = new ArrayList<>();

    public SystemEntity() {}

    public SystemEntity(List<Module> modules) {
        this.modules = modules;
    }

    public List<Module> getModules() {
        return modules;
    }

    public void setModules(List<Module> modules) {
        this.modules = modules;
    }
}
