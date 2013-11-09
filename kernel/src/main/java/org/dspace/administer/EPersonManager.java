/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.administer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.Locale;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.google.common.base.Objects;
import static com.google.common.base.Strings.*;
import static com.google.common.base.Preconditions.*;

import org.dspace.authorize.AuthorizeException;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.core.I18nUtil;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.EPersonDeletionException;
import org.dspace.eperson.Group;

/**
 * A utility class for managing (creating, modifying, deleting) EPersons.
 * Special use-case is creation of the first administrator of a new system.
 * The tool supports 3 modes of invocation/operation
 * (1) Interactive - user is prompted on a command-line for field values (creation only)
 * (2) Non-Interactive - action and all values passed on the command-line:
 * 
 * EPersonManager add -e [email] -g [given name] -s [surname] -l [language] -p [password] -a
 * 
 * (3) Programmatic - action and field values set on an instance in Java code:
 *
 * String status = new EPersonManager.email("a@b.com").password("foo").createEPerson(context);
 *
 * Derived from DSpace CreateAdministrator class.
 * 
 * @author richardrodgers
 * 
 */
public final class EPersonManager {

    // policy for actions, true => commit context with each action
    private boolean commitAtomic = true;

    enum Action {add, prompt, update, delete}
    @Argument(index=0, usage="action to take: add, prompt, update, or delete", required=true)
    private Action action;
    @Option(name="-a", usage="add to administrators group")
    private boolean admin;
	@Option(name="-e", usage="email address")
	private String email;
    @Option(name="-n", usage="netid")
    private String netid;
	@Option(name="-g", usage="given name")
	private String givenName;
	@Option(name="-s", usage="surname")
	private String surName;
	@Option(name="-l", usage="language")
	private String language;
	@Option(name="-p", usage="password")
	private String password;

    /**
     * Sets wheather actions are committed atiomically.
     * Default value is true
     */
    public void setAtomicCommit(boolean atomic) {
        commitAtomic = atomic;
    }

    /**
     * Sets wheather epersons are added to the adminstrator's group.
     * Default value is true
     */
    public EPersonManager setAdmin(boolean admin) {
        this.admin = admin;
        return this;
    }

    /**
     * Sets the email field
     */
    public EPersonManager email(String email) {
        this.email = checkNotNull(email);
        return this;
    }

    /**
     * Sets the netid field
     */
    public EPersonManager netid(String netid) {
        this.netid = checkNotNull(netid);
        return this;
    }

    /**
     * Sets the givenName field
     */
    public EPersonManager givenName(String givenName) {
        this.givenName = checkNotNull(givenName);
        return this;
    }

    /**
     * Sets the surName field
     */
    public EPersonManager surName(String surName) {
        this.surName = checkNotNull(surName);
        return this;
    }

    /**
     * Sets the language field
     */
    public EPersonManager language(String language) {
        this.language = checkNotNull(language);
        return this;
    }

    /**
     * Sets the password field
     */
    public EPersonManager password(String password) {
        this.password = checkNotNull(password);
        return this;
    }
	
    /**
     * For invoking via the command line.  If called with the 'prompt'
     * argument, it will prompt the user for the eperson details.
     * 
     * @param argv
     *            command-line arguments
     */
    public static void main(String[] args) throws Exception {
    	EPersonManager epm = new EPersonManager();
    	CmdLineParser parser = new CmdLineParser(epm);
        try {
        	parser.parseArgument(args);
            try (Context context = new Context()) {
                String status = null;
                epm.setAtomicCommit(false);
                switch (epm.action) {
                    case add    : status = epm.createEPerson(context); break;
                    case prompt : status = epm.negotiateDetails(context); break;
                    case update : status = epm.updateEPerson(context); break;
                    case delete : status = epm.deleteEPerson(context); break;
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
     * Method which will negotiate with the user via the command line to 
     * obtain the eperson's details
     * 
     * @throws Exception
     */
    private String negotiateDetails(Context context) throws Exception {
    	// For easier reading of typing
    	BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
    	
    	System.out.println("Creating an initial administrator account");
    	
    	boolean dataOK = false;
    	
    	language = I18nUtil.DEFAULTLOCALE.getLanguage();
    	
    	while (!dataOK) {
    		System.out.print("E-mail address: ");
    		System.out.flush();
    		
    		email = input.readLine();
            if (email != null) {
                email = email.trim();
            }
    		
    		System.out.print("Given name: ");
    		System.out.flush();
    		
    		givenName = input.readLine();
            if (givenName != null) {
                givenName = givenName.trim();
            }
    		
    		System.out.print("Surname: ");
    		System.out.flush();
    		
    		surName = input.readLine();
            if (surName != null) {
                surName = surName.trim();
            }
   		
            if (ConfigurationManager.getProperty("webui.supported.locales") != null)  {
                System.out.println("Select one of the following languages: " + ConfigurationManager.getProperty("webui.supported.locales"));
                System.out.print("Language: ");
                System.out.flush();
            
    		    language = input.readLine();
                if (language != null) {
                    language = language.trim();
                    language = I18nUtil.getSupportedLocale(new Locale(language)).getLanguage();
                }
            }
            
    		System.out.println("WARNING: Password will appear on-screen.");
    		System.out.print("Password: ");
    		System.out.flush();
    		
    		password = input.readLine();
            if (password != null) {
                password = password.trim();
            }
    		
    		System.out.print("Again to confirm: ");
    		System.out.flush();
    		
    		String password2 = input.readLine();
            if (password2 != null) {
                password2 = password2.trim();
            }
    		
    		if (! isNullOrEmpty(password) && Objects.equal(password, password2)) {
    			// password OK
    			System.out.print("Is the above data correct? (y or n): ");
    			System.out.flush();
    			
    			String s = input.readLine();

                if (s != null) {
                    s = s.trim();
                    if (s.toLowerCase().startsWith("y")) {
                        dataOK = true;
                    }
                }
    		} else {
    			System.out.println("Passwords don't match");
    		}
    	}
    	
    	// if we make it to here, we are ready to create an administrator
    	return createEPerson(context);
    }

    /**
     * Creates a new eperson record in the repository.
     */
    public String createEPerson(Context context) throws AuthorizeException, SQLException {

        checkState(email != null || netid != null, "An email address or netid must be specified");
        checkState(givenName != null, "Required field 'given name' missing");
        checkState(surName != null, "Required field 'surname' missing");
        //checkState(language != null, "Required field 'language' missing");
        checkState(password != null, "Required field 'password' missing");

        Group admins = null;
        if (admin) {
            // circumvent authorisation - this may be the 'bootstrap' administrator
            context.turnOffAuthorisationSystem();
            // Find administrator group
            admins = Group.find(context, 1);
            checkState(admins != null, "Error, no admin group (group 1) found");
        }
        // Create the e-person
        EPerson eperson = lookup(context);
        // default language if not present
        String lang = language;
        if (isNullOrEmpty(language)) {
            lang = I18nUtil.DEFAULTLOCALE.getLanguage();
        }
        
        // check if the email belongs to a registered user,
        // if not create a new user with this email
        if (eperson == null) {
            eperson = EPerson.create(context);
            eperson.setEmail(email);
            eperson.setCanLogIn(true);
            eperson.setRequireCertificate(false);
            eperson.setSelfRegistered(false);
        }
        
        eperson.setLastName(surName);
        eperson.setFirstName(givenName);
        eperson.setLanguage(language);
        eperson.setPassword(password);
        eperson.update();

        if (admin) {
            admins.addMember(eperson);
            admins.update();
        }

        if (commitAtomic) {
            context.commit();
        }
        return new StringBuilder().append("EPerson: '").append(givenName).append(" ").append(surName).append("'' created").toString();
    }

     public String updateEPerson(Context context) 
        throws AuthorizeException, EPersonDeletionException, SQLException {
        EPerson eperson = lookup(context);
        StringBuilder status = new StringBuilder();
        if (eperson != null) {
            if (! isNullOrEmpty(email)) {
                eperson.setEmail(email);
            }
            if (! isNullOrEmpty(netid)) {
                eperson.setNetid(netid);
            }
            if (! isNullOrEmpty(surName)) {
                eperson.setLastName(surName);
            }
            if (! isNullOrEmpty(givenName)) {
                eperson.setFirstName(givenName);
            }
            if (! isNullOrEmpty(language)) {
                eperson.setLanguage(language);
            }
            if (! isNullOrEmpty(password)) {
                eperson.setPassword(password);
            }
            eperson.update();
            if (commitAtomic) {
                context.commit();
            }
            return status.append("EPerson: '").append(givenName).append(" ").append(surName).append("'' updated").toString();
        } else {
            return status.append("No such EPerson: ").append(email).toString();
        }
    }

    public String deleteEPerson(Context context) 
        throws AuthorizeException, EPersonDeletionException, SQLException {
        EPerson eperson = lookup(context);
        StringBuilder status = new StringBuilder();
        if (eperson != null) {
            eperson.delete();
            if (commitAtomic) {
                context.commit();
            }
            return status.append("EPerson: '").append(givenName).append(" ").append(surName).append("'' deleted").toString();
        } else {
            return status.append("No such EPerson: ").append(email).toString();
        }
    }

    private EPerson lookup(Context context) throws AuthorizeException, SQLException {
        if (email != null) {
            return EPerson.findByEmail(context, email);
        } else if (netid != null) {
            return EPerson.findByNetid(context, netid);
        } else {
            return null;
        }
    }
}
