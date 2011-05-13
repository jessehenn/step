package com.tyndalehouse.step.core.service;

import com.tyndalehouse.step.core.data.entities.User;

/**
 * This service gives information about the user, allows his to register, does session management, etc.
 * 
 * <p />
 * Firstly, createSession() is called to register the server session. This is an anonymous session but helps
 * us keep track of concurrent users. We can possibly run a job on the server version to do some stats on the
 * ip addresses that we log.
 * 
 * Secondly, we expect the user might want to register to access some specific features (notes, bookmarks)
 * 
 * Thirdly, we expect a user to login with his [email/password] combo.
 * 
 * Fourthly, we can access everything through the session, without need for username, etc.
 * 
 * @author Chris
 * 
 */
public interface UserDataService {

    /**
     * Registers and stores the user details.
     * 
     * @param emailAddress the email address
     * @param name the name of the person [optional]
     * @param country his country [optional]
     * @param password the password he has chosen, which we should SHA-1 and salt
     * @return the user that has been created
     */
    User register(String emailAddress, String name, String country, String password);

    /**
     * Associates the current session with the username assuming password and username authenticates
     * 
     * @param emailAddress the email address is used as the login token
     * @param password the password
     * @return the user that has logged in
     */
    User login(String emailAddress, String password);

    /**
     * logs the current user out
     */
    void logout();

    /**
     * @return the logged in user if the user is logged in
     */
    User getLoggedInUser();

    /**
     * From a password, a number of iterations and a salt, returns the corresponding digest
     * 
     * @param iterationNb int The number of iterations of the algorithm
     * @param password String The password to encrypt
     * @param salt byte[] The salt
     * @return The digested password
     */
    byte[] getHash(int iterationNb, String password, byte[] salt);
}