
import java.util.HashSet;

import org.apache.directory.server.core.DefaultDirectoryService;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.partition.Partition;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmIndex;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmPartition;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.apache.directory.server.xdbm.Index;
import org.apache.directory.shared.ldap.entry.Entry;
import org.apache.directory.shared.ldap.exception.LdapNameNotFoundException;
import org.apache.directory.shared.ldap.name.LdapDN;

public class Server {
    /**
     * A simple example exposing how to embed Apache Directory Server
     * into an application.
     *
     * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
     * @version $Rev$, $Date$
     */
    public static class EmbeddedADS {
        /**
         * The directory service
         */
        private DirectoryService service;

        private LdapServer _ldapServer;

        /**
         * Add a new partition to the server
         *
         * @param partitionId The partition Id
         * @param partitionDn The partition DN
         * @return The newly added partition
         * @throws Exception If the partition can't be added
         */
        private Partition addPartition(String partitionId, String partitionDn) throws Exception {
            // Create a new partition named 'foo'.
            Partition partition = new JdbmPartition();
            partition.setId(partitionId);
            partition.setSuffix(partitionDn);
            service.addPartition(partition);

            return partition;
        }


        /**
         * Add a new set of index on the given attributes
         *
         * @param partition The partition on which we want to add index
         * @param attrs     The list of attributes to index
         */
        private void addIndex(Partition partition, String... attrs) {
            // Index some attributes on the apache partition
            HashSet<Index<?, ServerEntry>> indexedAttributes = new HashSet<Index<?, ServerEntry>>();

            for (String attribute : attrs) {
                indexedAttributes.add(new JdbmIndex<String, ServerEntry>(attribute));
            }

            ((JdbmPartition) partition).setIndexedAttributes(indexedAttributes);
        }


        /**
         * Initialize the server. It creates the partition, adds the index, and
         * injects the context entries for the created partitions.
         *
         * @throws Exception if there were some problems while initializing the system
         */
        private void init() throws Exception {
            // Initialize the LDAP service
            service = new DefaultDirectoryService();

            // Disable the ChangeLog system
            service.getChangeLog().setEnabled(false);
            service.setDenormalizeOpAttrsEnabled(true);

            // Create some new partitions named 'foo', 'bar' and 'apache'.
            Partition fooPartition = addPartition("foo", "dc=foo,dc=com");
            Partition barPartition = addPartition("bar", "dc=bar,dc=com");
            Partition apachePartition = addPartition("apache", "dc=apache,dc=org");

            // Index some attributes on the apache partition
            addIndex(apachePartition, "objectClass", "ou", "uid");

            // And start the service
            service.startup();

            // Inject the foo root entry if it does not already exist
            try {
                service.getAdminSession().lookup(fooPartition.getSuffixDn());
            } catch (LdapNameNotFoundException lnnfe) {
                LdapDN dnFoo = new LdapDN("dc=foo,dc=com");
                ServerEntry entryFoo = service.newEntry(dnFoo);
                entryFoo.add("objectClass", "top", "domain", "extensibleObject");
                entryFoo.add("dc", "foo");
                service.getAdminSession().add(entryFoo);
            }

            // Inject the bar root entry
            try {
                service.getAdminSession().lookup(barPartition.getSuffixDn());
            } catch (LdapNameNotFoundException lnnfe) {
                LdapDN dnBar = new LdapDN("dc=bar,dc=com");
                ServerEntry entryBar = service.newEntry(dnBar);
                entryBar.add("objectClass", "top", "domain", "extensibleObject");
                entryBar.add("dc", "bar");
                service.getAdminSession().add(entryBar);
            }

            // Inject the apache root entry
            if (!service.getAdminSession().exists(apachePartition.getSuffixDn())) {
                LdapDN dnApache = new LdapDN("dc=Apache,dc=Org");
                ServerEntry entryApache = service.newEntry(dnApache);
                entryApache.add("objectClass", "top", "domain", "extensibleObject");
                entryApache.add("dc", "Apache");
                service.getAdminSession().add(entryApache);
            }

            // We are all done !
        }


        /**
         * Creates a new instance of EmbeddedADS. It initializes the directory service.
         *
         * @throws Exception If something went wrong
         */
        public EmbeddedADS() throws Exception {
            init();
        }

        public LdapServer getLdapServer() {
            return _ldapServer;
        }

        public void setLdapServer(LdapServer ldapServer) {
            this._ldapServer = ldapServer;
        }

        public void start() throws Exception {
            if (getLdapServer() == null) {
                setLdapServer(new LdapServer());
                getLdapServer().setDirectoryService(service);
                getLdapServer().setTransports(new TcpTransport(10389));
                getLdapServer().start();
            }
        }
    }

    /**
     * Main class. We just do a lookup on the server to check that it's available.
     *
     * @param args Not used.
     */
    public static void main(String[] args) //throws Exception
    {
        try {
            // Create the server
            EmbeddedADS ads = new EmbeddedADS();
            ads.start();
            System.out.println("服务启动成功:" + ads.getLdapServer().getPort());

            // test Entry result = ads.service.getAdminSession().lookup( new LdapDN( "dc=apache,dc=org" ) );
        } catch (Exception e) {
            // Ok, we have something wrong going on ...
            e.printStackTrace();
        }
    }
}
