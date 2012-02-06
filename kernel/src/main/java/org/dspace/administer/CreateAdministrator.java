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
import java.util.Locale;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.google.common.base.Objects;
import com.google.common.base.Strings;

import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.core.I18nUtil;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;

/**
 * A command-line tool for creating an initial administrator for setting up a
 * DSpace site. Prompts for an e-mail address, last name, first name and
 * password from standard input. An administrator group is then created and the
 * data passed in used to create an e-person in that group.
 * <P>
 * Alternatively, it can be used to take the email, first name, last name and
 * desired password as arguments thus:
 * 
 * CreateAdministrator -e [email] -f [first name] -l [last name] -p [password]
 * 
 * This is particularly convenient for automated deploy scripts that require an 
 * initial administrator, for example, before deployment can be completed
 * 
 * @author Robert Tansley
 * @author Richard Jones
 * 
 */
public final class CreateAdministrator
{
	/** DSpace Context object */
	private Context context;
	
	// email address
	@Option(name="-e", usage="administrator email address", required=true)
	private String email;
	
	// first name 
	@Option(name="-f", usage="administrator first name", required=true)
	private String firstName;
	
	// last name 
	@Option(name="-l", usage="administrator last name", required=true)
	private String lastName;
	
	// language
	@Option(name="-c", usage="administrator language", required=true)
	private String language;
	
	// language
	@Option(name="-p", usage="administrator password", required=true)
	private String password;
	
    /**
     * For invoking via the command line.  If called with no command line arguments,
     * it will negotiate with the user for the administrator details
     * 
     * @param argv
     *            command-line arguments
     */
    public static void main(String[] args) throws Exception
    {
    	CreateAdministrator ca = new CreateAdministrator();
    	CmdLineParser parser = new CmdLineParser(ca);
        try {
        	parser.parseArgument(args);
        	ca.createAdministrator();
        }  catch (CmdLineException clE) {
        	System.err.println(clE.getMessage());
        	parser.printUsage(System.err);
        }
    	
    	/*
    	if (line.hasOption("e") && line.hasOption("f") && line.hasOption("l") &&
    			line.hasOption("c") && line.hasOption("p"))
    	{
    		ca.createAdministrator(line.getOptionValue("e"),
    				line.getOptionValue("f"), line.getOptionValue("l"),
    				line.getOptionValue("c"), line.getOptionValue("p"));
    	}
    	else
    	{
    		ca.negotiateAdministratorDetails();
    	}
    	*/
    }
    
    /** 
     * constructor, which just creates and object with a ready context
     * 
     * @throws Exception
     */
    private CreateAdministrator() throws Exception
    {
    	context = new Context();
    }
    
    /**
     * Method which will negotiate with the user via the command line to 
     * obtain the administrator's details
     * 
     * @throws Exception
     */
    private void negotiateAdministratorDetails() throws Exception {
    	// For easier reading of typing
    	BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
    	
    	System.out.println("Creating an initial administrator account");
    	
    	boolean dataOK = false;
    	
    	language = I18nUtil.DEFAULTLOCALE.getLanguage();
    	
    	while (!dataOK)
    	{
    		System.out.print("E-mail address: ");
    		System.out.flush();
    		
    		email = input.readLine();
            if (email != null) {
                email = email.trim();
            }
    		
    		System.out.print("First name: ");
    		System.out.flush();
    		
    		firstName = input.readLine();
            if (firstName != null) {
                firstName = firstName.trim();
            }
    		
    		System.out.print("Last name: ");
    		System.out.flush();
    		
    		lastName = input.readLine();
            if (lastName != null) {
                lastName = lastName.trim();
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
    		
    		if (!Strings.isNullOrEmpty(password) && Objects.equal(password, password2))
    		{
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
    	createAdministrator();
    }
    
    /**
     * Create the administrator with the given details.  If the user
     * already exists then they are simply upped to administrator status
     * 
     * @throws Exception
     */
    private void createAdministrator() throws Exception
    {
    	// Of course we aren't an administrator yet so we need to
    	// circumvent authorisation
    	context.turnOffAuthorisationSystem();
    	
    	// Find administrator group
    	Group admins = Group.find(context, 1);
    	
    	if (admins == null) {
    		throw new IllegalStateException("Error, no admin group (group 1) found");
    	}
    	
    	// Create the administrator e-person
        EPerson eperson = EPerson.findByEmail(context, email);
        
        // check if the email belongs to a registered user,
        // if not create a new user with this email
        if (eperson == null) {
            eperson = EPerson.create(context);
            eperson.setEmail(email);
            eperson.setCanLogIn(true);
            eperson.setRequireCertificate(false);
            eperson.setSelfRegistered(false);
        }
    	
    	eperson.setLastName(lastName);
    	eperson.setFirstName(firstName);
    	eperson.setLanguage(language);
    	eperson.setPassword(password);
    	eperson.update();
    	
    	admins.addMember(eperson);
    	admins.update();
    	
    	context.complete();
    	
    	System.out.println("Administrator account created");
    }
}
