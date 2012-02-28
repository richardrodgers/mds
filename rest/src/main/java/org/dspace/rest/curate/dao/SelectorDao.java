/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.rest.curate.dao;

import java.util.ArrayList;
import java.util.List;

import org.dspace.core.ConfigurationManager;
import org.dspace.rest.curate.domain.Selector;
import org.dspace.rest.curate.domain.SelectorGroup;

/**
 * SelectorDao provides domain objects to the service.
 * Currently, their definitions reside in DSpace configuration
 * files, but this is not optimal.
 *  
 * @author richardrodgers
 *
 */

public class SelectorDao {
	
	public List<Selector> getSelectors() {
		// Currently read from curate.cfg - belong elsewhere
		String selProp = ConfigurationManager.getProperty("curate", "ui.selectors");
		List<Selector> selList = new ArrayList<Selector>();
		if (selProp != null) {
			for (String desc : selProp.split(",")) {
				String[] parts = desc.split("=");
				Selector selector = new Selector();
				selector.setName(parts[0].trim());
				selector.setDescription(parts[1].trim());
				selList.add(selector);
			}
		}
		return selList;
	}
	
	public List<SelectorGroup> getSelectorGroups() {
		// Currently read from curate.cfg - belong elsewhere
		String sgProp = ConfigurationManager.getProperty("curate", "ui.selectorgroups");
		List<SelectorGroup> groupList = new ArrayList<SelectorGroup>();
		if (sgProp != null) {
			List<Selector> allSelectors = getSelectors();
			List<Selector> members = new ArrayList<Selector>();
			for (String desc : sgProp.split(",")) {
				String[] parts = desc.split("=");
				SelectorGroup group = new SelectorGroup();
				group.setName(parts[0].trim());
				group.setDescription(parts[1].trim());
				String memProp = ConfigurationManager.getProperty("curate", "ui.selectorgroup." + group.getName());
				for (String mem : memProp.split(",")) {
					Selector memSelector = containsSelector(allSelectors, mem.trim());
					if (memSelector != null) {
						members.add(memSelector);
					}
				}
				group.setMembers(members);
				groupList.add(group);
			}
		}
		return groupList;
	}
	
	private Selector containsSelector(List<Selector> selectorList, String selectorName) {
		for (Selector selector : selectorList) {
			if (selector.getName().equals(selectorName)) {
				return selector;
			}
		}
		return null;
	}
}
