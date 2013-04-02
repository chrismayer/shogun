/**
 *
 */
package de.terrestris.shogun.service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.encoding.Md5PasswordEncoder;
import org.springframework.security.authentication.encoding.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.terrestris.shogun.exception.ShogunDatabaseAccessException;
import de.terrestris.shogun.exception.ShogunServiceException;
import de.terrestris.shogun.model.Group;
import de.terrestris.shogun.model.MapConfig;
import de.terrestris.shogun.model.Module;
import de.terrestris.shogun.model.Role;
import de.terrestris.shogun.model.User;
import de.terrestris.shogun.model.WfsProxyConfig;
import de.terrestris.shogun.model.WmsProxyConfig;
import de.terrestris.shogun.util.Mail;
import de.terrestris.shogun.util.Password;

/**
 * A service class of SHOGun offering user related business logic.
 *
 * @author terrestris GmbH & Co. KG
 *
 */
@Service
public class UserAdministrationService extends AbstractShogunService {

	/**
	 *
	 */
	private static Logger LOGGER = Logger.getLogger(UserAdministrationService.class);

	/**
	 * User defined by the given userId gets a new password. Password is
	 * generated by random and will be sent to the User via email.
	 *
	 * @param userId the ID of the {@link User} object in the database
	 * @throws ShogunServiceException If an error arises
	 */
	@Transactional
	public void updateUserPassword(String userId) throws ShogunServiceException {

		User user = null;
		try {
			// create an integer out of the id-String
			int iUserId = Integer.parseInt(userId);
			// get the User object
			user = (User) this.getDatabaseDao().getEntityById(iUserId, User.class);

		} catch (Exception e) {
			throw new ShogunServiceException(
					"Error while getting User from database with ID " +
					userId + " " + e.getMessage());
		}

		try {
			// set the new password
			String newPassword = Password.getRandomPassword(8);

			PasswordEncoder pwencoder = new Md5PasswordEncoder();
			String hashed = pwencoder.encodePassword(newPassword, null);

			user.setUser_password(hashed);

			// write back to database
			this.getDatabaseDao().updateUser(user);

			// send an email with the new password
			//TODO remove static texts
			//TODO remove static mail server settings
			String mailtext = "Sehr geehrter Nutzer " + user.getUser_name()
					+ "\n\n";
			mailtext += "Ihr SHOGun-Passwort wurde geändert und lautet \n\n";
			mailtext += newPassword + "\n\n";

			Mail.send("localhost", user.getUser_email(),
					"admin", "Passwort-Änderung bei SHOGun", mailtext);

		} catch (Exception e) {
			throw new ShogunServiceException(
					"Error while updating User in database or while " +
					"sending email. " + e.getMessage());
		}
	}

	/**
	 *
	 * Inserts new {@link User} objects into the database. The new objects are
	 * send to the DAO to save them into the DB. <br>
	 * It is checked if the corresponding group has no other User with the same name. <br>
	 * Before saving the User object:
	 * <ul>
	 * <li>a random password is generated and save in the User instance</li>
	 * <li>new password is sent to the user via email</li>
	 * <li>create a {@link Module} object list from the comma-separated list of
	 * module IDs (user_module_list)</li>
	 * </ul>
	 *
	 * <b>CAUTION: Only if the logged in user has the role ROLE_ADMIN the
	 * function is accessible, otherwise access is denied.</b>
	 *
	 * @param newUsers
	 *            a list of {@link User} objects to be inserted
	 * @return a list of {@link User} objects which have been inserted
	 * @throws ShogunDatabaseAccessException
	 * @throws ShogunServiceException
	 * @throws Exception
	 *             if a user with the same name
	 *             already exists
	 */
	@Transactional
	@PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_SUPERADMIN')")
	public List<User> createUsers(List<User> newUsers) throws ShogunDatabaseAccessException, ShogunServiceException {

		List<User> returnUsers = new ArrayList<User>();

		// iterate over user delivered by client request
		for (Iterator<User> iterator = newUsers.iterator(); iterator.hasNext();) {

			User user = iterator.next();

			User newuser = null;

			// CREATE A NEW USER

			// check if there is an existing user with the same name
			// if there is --> ERROR
			List<User> testUser = this.getDatabaseDao().getUserByName(user.getUser_name());
			if (testUser.size() > 0) {
				throw new ShogunServiceException(
						"User with name " + user.getUser_name()
						+ " already exists!");
			}

			// create a new random password and send it per mail to new user
			String pw = Password.getRandomPassword(8);

			PasswordEncoder pwencoder = new Md5PasswordEncoder();
			String hashed = pwencoder.encodePassword(pw, null);

			user.setUser_password(hashed);

			// TODO become more flexibel here, wrap to method
			// set the default map conf
			// TODO remove static ID !!!!!
			MapConfig mapConfig =
				(MapConfig)this.getDatabaseDao().getEntityById(1, MapConfig.class);
			if (mapConfig != null) {
//					Set newMapConfSet = new HashSet<MapConfig>();
//					newMapConfSet.add(mapConfig);
//					user.setMapConfigs(newMapConfSet);

				user.setMapConfig(mapConfig);
			}

			// TODO become more flexibel here, wrap to method
			// set the default wms proxy conf
			// TODO remove static ID !!!!!
			WmsProxyConfig defaultWmsProxy =
				(WmsProxyConfig)this.getDatabaseDao().getEntityById(1, WmsProxyConfig.class);
			if (defaultWmsProxy != null) {
				user.setWmsProxyConfig(defaultWmsProxy);
			}

			// TODO become more flexibel here, wrap to method
			// set the default wms proxy conf
			// TODO remove static ID !!!!!
			WfsProxyConfig defaultWfsProxy =
				(WfsProxyConfig)this.getDatabaseDao().getEntityById(1, WfsProxyConfig.class);
			if (defaultWfsProxy != null) {
				user.setWfsProxyConfig(defaultWfsProxy);
			}

			//TODO remove this static mail text
			String mailtext = "Sehr geehrter Nutzer " + user.getUser_name()
					+ "\n\n";
			mailtext += "Ihr Passwort zur Terrestris Suite lautet \n\n";
			mailtext += pw + "\n\n";

			Mail.send("localhost", user.getUser_email(), "admin", "Registrierung bei SHOGun", mailtext);

			user.transformSimpleModuleListToModuleObjects(this.getDatabaseDao());

			// write in DB
			// do setSessionGroup only in case of beeing NO SuperAdmin
			newuser = this.getDatabaseDao().createUser(user, "ROLE_USER", true);

			LOGGER.debug(" USER RETURNED: " + newuser.getId());

			returnUsers.add(newuser);
		}

		return returnUsers;
	}

	/**
	 * Updates User objects in the database. The changed objects are send to the
	 * DAO to save them into the DB. <br>
	 * <b>CAUTION: Only if the logged in user has the role ROLE_ADMIN the
	 * function is accessible, otherwise access is denied.</b>
	 *
	 * @param updatedUsers a list of {@link User} objects to be updated.
	 * @return a list of {@link User} objects which have been updated
	 * @throws ShogunDatabaseAccessException
	 * @throws ShogunServiceException
	 *
	 */
	@Transactional
	@PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_SUPERADMIN')")
	public List<User> updateUser(List<User> updatedUsers) throws ShogunDatabaseAccessException, ShogunServiceException {

		List<User> returnUsers = new ArrayList<User>();

		// iterate over user delivered by client request
		for (Iterator<User> iterator = updatedUsers.iterator(); iterator.hasNext();) {

			// user to be updated
			User user = iterator.next();

			List<Module> newModules = null;

			// create module object list
			user.transformSimpleModuleListToModuleObjects(this.getDatabaseDao());

			newModules = null;

			// Check if logged-in user has the same group than the
			// user to be updated
			List<Integer> groupsOfSessionUser = this.getDatabaseDao().getGroupIdsFromSession();
			User oldUser = this.getDatabaseDao().getUserByName(
					user.getUser_name(),
					"groups",
					Restrictions.in("id", groupsOfSessionUser));

			// no user found in own groups --> exception
			if (oldUser == null) {
				throw new ShogunServiceException(
						"The User to be updated is not accessible for the " +
						"logged in user!");
			}

			// if password is empty/null in request from client, keep the old
			// one
			if (user.getUser_password() == null) {
				user.setUser_password(oldUser.getUser_password());
			}

			// TODO make this configurable

			// if wfs/wms conf is empty in request from client, keep the old
			// one
			if (user.getWfsProxyConfig() == null) {
				user.setWfsProxyConfig(oldUser.getWfsProxyConfig());
			}
			if (user.getWmsProxyConfig() == null) {
				user.setWmsProxyConfig(oldUser.getWmsProxyConfig());
			}
			// if map conf is empty in request from client, keep the old one
			if (user.getMapConfig() == null) {
				user.setMapConfig(oldUser.getMapConfig());
			}

			// if mapLayers is empty in request from client, keep the old
			// one
			if (user.getMapLayers() == null) {
				user.setMapLayers(oldUser.getMapLayers());
			}

			// if roles is empty in request from client, keep the old
			// one
			if (user.getRoles() == null) {
				user.setRoles(oldUser.getRoles());
			}

			// write in DB
			User updatedUser = this.getDatabaseDao().updateUser(user);

			returnUsers.add(updatedUser);

			// clear the session cache in order to have to current updated
			// objects
			// when requesting again
			// unfortunately sess.evict does not work
			this.getDatabaseDao().clearSession();
		}

		return returnUsers;
	}

	/**
	 * Delete a {@link User} object, defined by its ID, from the database
	 *
	 * @param deleteId
	 * @throws ShogunDatabaseAccessException
	 * @throws Exception
	 */
	@Transactional
	@PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_SUPERADMIN')")
	public void deleteUser(int deleteId) throws ShogunDatabaseAccessException {

		// it is only one object - cast to object/bean
		Integer id = new Integer(deleteId);

		if (this.getDatabaseDao().isSuperAdmin()) {
			this.getDatabaseDao().deleteUser(id);
		} else {
			this.getDatabaseDao().deleteEntityGroupDependent(User.class, id);
		}
	}


	/**
	 * Inserts a new {@link Group} object into the database. <br>
	 * It is checked if there is no other Group with the same number. <br>
	 * Before saving the Group object:
	 * <ul>
	 * <li>create a {@link Module} object list from the comma-separated list of
	 * module IDs (user_module_list)</li>
	 * <li>a sub-admin as {@link User} instance is created with the base values
	 * of the Group instance (street, etc.) in case of triggering this method
	 * as SUPER_ADMIN</li>
	 * <li>a random password is generated and save in the sub-admin instance</li>
	 * <li>the new password is sent to the sub-admin via email</li>
	 * </ul>
	 *
	 * <b>CAUTION: Only if the logged in user has the role ROLE_ADMIN or
	 * ROLE_SUPERADMIN the
	 * function is accessible, otherwise access is denied.</b>
	 *
	 * @param group a {@link Group} object to be inserted
	 * @return the {@link Group} object which has been inserted
	 * @throws ShogunServiceException if a Group with the same group_nr already exists
	 */
	@Transactional
	@PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_SUPERADMIN')")
	public Group createGroup(Group group) throws ShogunServiceException, ShogunDatabaseAccessException {

		Group newGroup = null;

		// check if there is an existing user with the same number
		// if there is --> Exception
		Group testGroup = null;
		testGroup = (Group) this.getDatabaseDao().getEntityByStringField(
				Group.class, "group_nr", group.getGroup_nr());
		if (testGroup != null) {
			throw new ShogunServiceException(
					"Group with number " + group.getGroup_nr()
					+ " already exists!");
		}

		group.transformSimpleModuleListToModuleObjects(this.getDatabaseDao());

		// write new Group in DB
		newGroup = (Group) this.getDatabaseDao().createEntity(
				Group.class.getSimpleName(), group);

		LOGGER.debug("Group created in database with ID: " + newGroup.getId());

		// check if the logged in user has the ROLE_ADMIN,
		// so we do not need to create an admin as group leader,
		// the logged in User is the group leader
		User sessionUser = this.getDatabaseDao().getUserObjectFromSession();
		Set<Role> rolesOfSessionUser = sessionUser.getRoles();
		boolean isAdmin = false;
		for (Role role : rolesOfSessionUser) {
			if (role.getName().equals(User.ROLENAME_ADMIN) == true) {
				isAdmin = true;
				break;
			}
		}

		User persistentSubadmin = null;
		if (isAdmin == true) {
			persistentSubadmin = sessionUser;
		} else {
			persistentSubadmin = this.createSubadminForGroup(newGroup);
		}

		// add sub-admin to the created group
		newGroup.getUsers().add(persistentSubadmin);
		this.getDatabaseDao().updateEntity("Group", newGroup);

		return newGroup;
	}

	/**
	 * Updates Group objects in the database. <br>
	 * <b>CAUTION: Only if the logged in user has the role ROLE_SUPERADMIN the
	 * function is accessible, otherwise access is denied.</b>
	 *
	 *
	 * @param updatedGroups
	 *            a list of {@link Group} objects which should be updated
	 * @return a list of {@link Group} objects which have been successfully
	 *         updated
	 * @throws ShogunDatabaseAccessException
	 *
	 * TODO CM refactor
	 */
	@Transactional
	@PreAuthorize("hasRole('ROLE_SUPERADMIN')")
	public List<Group> updateGroup(List<Group> updatedGroups) throws ShogunDatabaseAccessException {

		List<Group> returnGroups = new ArrayList<Group>();

		// iterate over Group delivered by client request
		for (Iterator<Group> iterator = updatedGroups.iterator(); iterator
				.hasNext();) {
			Group group = iterator.next();

			// transform the comma-separated list of module IDs to a list of
			// Module objects
			group.transformSimpleModuleListToModuleObjects(this.getDatabaseDao());

			// write in DB
			Group updatedGroup = (Group) this.getDatabaseDao().updateEntity(
					Group.class.getSimpleName(), group);

			// fetch via group_nr because it is not changeable and unique
			// restrict the access to the given group
			User subadmin = this.getDatabaseDao().getUserByName(
					"subadmin_" + updatedGroup.getGroup_nr(),
					"groups",
					Restrictions.eq("id", updatedGroup.getId()));

			// Overwrite
			subadmin.setUser_module_list(updatedGroup.getGroup_module_list());
			subadmin.transformSimpleModuleListToModuleObjects(this.getDatabaseDao());

			// update
			this.getDatabaseDao().updateUser(subadmin);

			returnGroups.add(updatedGroup);

			// clear the session cache in order to have to current updated
			// objects
			// when requesting again
			// unfortunately sess.evict does not work
			this.getDatabaseDao().clearSession();

		}

		return returnGroups;
	}

	/**
	 * Deletes an Group object from database. Record is specified by its ID <br>
	 * <b>CAUTION: Only if the logged in user has the role ROLE_SUPERADMIN the
	 * function is accessible, otherwise access is denied.</b>
	 *
	 * @param deleteId
	 *            the ID of the record to be deleted
	 * @throws ShogunDatabaseAccessException
	 */
	@Transactional
	@PreAuthorize("hasRole('ROLE_SUPERADMIN')")
	public void deleteGroup(int deleteId) throws ShogunDatabaseAccessException {

//		// delete all User records of the given group,
//		// which has to be deleted
//		this.getDatabaseDao().deleteGroupUsers(deleteId);

		//TODO CM ensure no users are delted here

		Integer id = new Integer(deleteId);
		this.getDatabaseDao().deleteEntity(Group.class, id);
	}

	/**
	 * Creates a {@link User} object which acts a sub-admin (= group leader) for
	 * the given Group.
	 *
	 * @param group A persistent {@link Group} object to create a sub-admin for
	 * @return the persistent {@link User} object which acts a sub-admin
	 * @throws ShogunServiceException
	 */
	private User createSubadminForGroup(Group group) throws ShogunServiceException {

		try {

			// create a USER object as sub-admin
			User subadmin = new User();
			subadmin.setUser_country(group.getCountry());
			subadmin.setUser_email(group.getMail());
			subadmin.setUser_name("subadmin_" + group.getGroup_nr());
			subadmin.setUser_street(group.getStreet());
			subadmin.setUser_lang(group.getLanguage());
			subadmin.setUser_module_list(group.getGroup_module_list());

			// create ModuleArray from Module-comma-separated list
			subadmin.transformSimpleModuleListToModuleObjects(this.getDatabaseDao());

			// TODO become more flexibel here, wrap to method
			// set the default map conf
			// TODO remove static ID !!!!!!!
			MapConfig mapConfig =
				(MapConfig)this.getDatabaseDao().getEntityById(1, MapConfig.class);
			if (mapConfig != null) {
	//				Set newMapConfSet = new HashSet<MapConfig>();
	//				newMapConfSet.add(mapConfig);
	//				subadmin.setMapConfigs(newMapConfSet);

				subadmin.setMapConfig(mapConfig);
			}
			// TODO become more flexibel here, wrap to method
			// set the default wms proxy conf
			WmsProxyConfig defaultWmsProxy =
				(WmsProxyConfig)this.getDatabaseDao().getEntityById(1, WmsProxyConfig.class);
			if (defaultWmsProxy != null) {
				subadmin.setWmsProxyConfig(defaultWmsProxy);
			}

			// TODO become more flexibel here, wrap to method
			// set the default wms proxy conf
			WfsProxyConfig defaultWfsProxy =
				(WfsProxyConfig)this.getDatabaseDao().getEntityById(1, WfsProxyConfig.class);
			if (defaultWfsProxy != null) {
				subadmin.setWfsProxyConfig(defaultWfsProxy);
			}

			subadmin.setApp_user(group.getApp_user());
			subadmin.setCreated_at(group.getCreated_at());
			subadmin.setUpdated_at(group.getUpdated_at());

			// create a new random password and send it per mail to new user
			String pw = Password.getRandomPassword(8);

			PasswordEncoder pwencoder = new Md5PasswordEncoder();
			String hashed = pwencoder.encodePassword(pw, null);

			subadmin.setUser_password(hashed);

			// save sub-admin to database
			User persistentSubadmin =
				this.getDatabaseDao().createUser(
						subadmin, User.ROLENAME_ADMIN, false);

			// send a mail with the new password
			String mailtext = "Sehr geehrter Nutzer " + subadmin.getUser_name()
					+ "\n\n";
			mailtext += "Ihr Passwort zu SHOGun lautet \n\n";
			mailtext += pw + "\n\n";

			Mail.send("localhost", subadmin.getUser_email(), "admin",
					"Registrierung bei SHOGun", mailtext);


			return persistentSubadmin;

		} catch (Exception e) {
			throw new ShogunServiceException(
					"Error while creating sub-admin for group: " + e.getMessage(), e);
		}
	}
}
