import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Interface used for the remote method invocation. The registration to the
 * quizzle service is impeented by RMI.
 */
public interface RegistrationRMI extends Remote {
    /**
     * The remote method that implements the registration of a user to the
     * {@code QuizzleServer} service.
     * 
     * @param username the user's username.
     * @param password the user's password.
     * @return returns a message.
     * @throws RemoteException if something fishy happens during the remote method
     *                         invocation.
     */
    public String registerUser(String username, String password) throws RemoteException;
}
