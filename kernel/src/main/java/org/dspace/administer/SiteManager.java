/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.administer;

import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.google.common.base.Objects;
import static com.google.common.base.Strings.*;
import static com.google.common.base.Preconditions.*;

import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Context;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.FormatIdentifier;
import org.dspace.content.Site;
import org.dspace.eperson.EPerson;

/**
 * A utility class for managing (creating, modifying, deleting) the site data.
 * The tool supports 2 modes of invocation/operation
 * (1) Commandline - action and all values passed on a command-line:
 * 
 * SiteManager create -n [site name] -l [logo file name] -e [eperson email]
 * 
 * (2) Programmatic - action and field values set on an instance in Java code:
 *
 * String status = new SiteManager().name("My Site").eperson("a@b.com").createSite(context);
 * 
 * @author richardrodgers
 * 
 */
public final class SiteManager {

    enum Action {create, update, delete}
    @Argument(index=0, usage="action to take: create, update, or delete", required=true)
    private Action action;
    @Option(name="-n", usage="site name")
    private String name;
    @Option(name="-l", usage="logo file")
    private String logoFile;
    @Option(name="-e", usage="eperson email")
    private String eperson;

    /**
     * Sets the name field
     */
    public SiteManager name(String name) {
        this.name = checkNotNull(name);
        return this;
    }

    /**
     * Sets the logoFile field
     */
    public SiteManager logoFile(String logoFile) {
        this.logoFile = checkNotNull(logoFile);
        return this;
    }

    /**
     * Sets the eperson field
     */
    public SiteManager eperson(String eperson) {
        this.eperson = checkNotNull(eperson);
        return this;
    }

    /**
     * For invoking via the command line.
     * 
     * @param argv
     *            command-line arguments
     */
    public static void main(String[] args) throws Exception {
        SiteManager sm = new SiteManager();
        CmdLineParser parser = new CmdLineParser(sm);
        try {
            parser.parseArgument(args);
            try (Context context = new Context()) {
                String status = null;
                switch (sm.action) {
                    case create : status = sm.createSite(context); break;
                    case update : status = sm.updateSite(context); break;
                    case delete : status = sm.deleteSite(context); break;
                    default: status = "unknown action: " + sm.action; break;
                }
                context.complete();
                System.out.println(status);
            }
        }  catch (CmdLineException clE) {
            System.err.println(clE.getMessage());
            parser.printUsage(System.err);
        }
    }

    /**
     * Creates a new site record in the repository.
     */
    public String createSite(Context context) throws AuthorizeException, IOException, SQLException {
        checkState(name != null, "Required field 'name' missing");
        setUser(context);
        Site site = Site.find(context, 1);
        checkState(site == null, "Error, site already exists");
        
        site = Site.create(context);
        site.setName(name);
        if (! isNullOrEmpty(logoFile)) {
            uploadLogo(site, context);
        }
        
        site.update();
        context.commit();
        return "Site: '" + name + "' created";
    }

     public String updateSite(Context context) throws AuthorizeException, IOException, SQLException {
        setUser(context);
        Site site = Site.find(context, 1);
        checkState(site != null, "Error, no extant site");
        if (site != null) {
            if (! isNullOrEmpty(name)) {
                site.setName(name);
            }
            if (! isNullOrEmpty(logoFile)) {
                uploadLogo(site, context);
            }
            site.update();
            context.commit();
            return "Site: '" + name + "' updated";
        } else {
            return "No extant Site";
        }
    }

    public String deleteSite(Context context) throws AuthorizeException, SQLException, IOException {
        setUser(context);
        Site site = Site.find(context, 1);
        checkState(site != null, "Error, no extant site");
        String name = site.getName();
        site.delete();
        context.commit();
        return "Site: '" + name + "' deleted";
    }

    private void uploadLogo(Site site, Context context) throws AuthorizeException, SQLException, IOException {
        // Get an input stream to logo
        Path logoPath = Paths.get(logoFile);
        InputStream logoStream = Files.newInputStream(logoPath);
        Bitstream logo = site.setLogo(logoStream);
        logoStream.close();
        // OK - using the crude filename method, assign the logo bitstream format
        logo.setName(logoFile);
        logo.setFormat(FormatIdentifier.guessFormat(context, logo));
        logo.update();
    }

    private void setUser(Context context) throws AuthorizeException, SQLException {
        if (eperson != null) {
            EPerson user = EPerson.findByEmail(context, eperson);
            context.setCurrentUser(user);
        }
    }
}
