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
 * ServerEntity contains basic information about
 * the application runtime environment (OS, Java, etc)
 *
 * @author richardrodgers
 */

@XmlRootElement(name="server")
public class ServerEntity {

    private String osName;
    private String osArch;
    private String osVersion;
    private String javaVendor;
    private String javaVersion;
    private String containerName;
    private String containerVersion;

    public ServerEntity() {}

    public ServerEntity(String containerInfo) {
        String[] container = containerInfo.split("/");
        // Servlet container
        containerName = container[0];
        containerVersion = container[1];
        // OS
        osName = System.getProperty("os.name");
        osArch = System.getProperty("os.arch");
        osVersion = System.getProperty("os.version");
        // Java
        javaVendor = System.getProperty("java.vendor");
        javaVersion = System.getProperty("java.version");
    }

    public String getOsName() {
        return osName;
    }

    public void setOsName(String osName) {
         this.osName = osName;
    }

    public String getOsArch() {
        return osArch;
    }

    public void setOsArch(String osArch) {
         this.osArch = osArch;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
         this.osVersion = osVersion;
    }

    public String getJavaVendor() {
        return javaVendor;
    }

    public void setJavaVendor(String javaVendor) {
         this.javaVendor = javaVendor;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public void setJavaVersion(String javaVersion) {
         this.javaVersion = javaVersion;
    }

    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String containerName) {
         this.containerName = containerName;
    }

    public String getContainerVersion() {
        return containerVersion;
    }

    public void setContainerVersion(String containerVersion) {
         this.containerVersion = containerVersion;
    }
}
