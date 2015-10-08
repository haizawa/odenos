package com.datastax.logging.appender;


import java.io.FileInputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import com.datastax.driver.core.*;

import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.codehaus.jackson.map.ObjectMapper;

import com.datastax.driver.core.policies.RoundRobinPolicy;
import com.google.common.base.Joiner;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Main class that uses Cassandra to store log entries into.
 * 
 */
@Plugin(name = "CassandraAppender", category = "Core", elementType = "appender", printObject = true)
public class CassandraAppender extends AbstractAppender
{
  private static final long serialVersionUID = -9152033922423544771L;

  // Cassandra configuration
  private String hosts = "localhost";
  private int port = 9042; //for the binary protocol, 9160 is default for thrift
  private String username = "";
  private String password = "";
  private static final String ip = getIP();
  private static final String hostname = getHostName();

  // Encryption.  sslOptions and authProviderOptions are JSON maps requiring Jackson
  private static final ObjectMapper jsonMapper = new ObjectMapper();
  private Map<String, String> sslOptions = null;
  private Map<String, String> authProviderOptions = null;

  // Keyspace/ColumnFamily information
  private String keyspaceName = "Logging";
  private String columnFamily = "log_entries";
  private String appName = "default";
  private String replication = "{ 'class' : 'SimpleStrategy', 'replication_factor' : 1 }";
  private ConsistencyLevel consistencyLevelWrite = ConsistencyLevel.ONE;

  // CF column names
  public static final String ID = "key";
  public static final String HOST_IP = "host_ip";
  public static final String HOST_NAME = "host_name";
  public static final String APP_NAME = "app_name";
  public static final String LOGGER_NAME = "logger_name";
  public static final String LEVEL = "level";
  public static final String CLASS_NAME = "class_name";
  public static final String FILE_NAME = "file_name";
  public static final String LINE_NUMBER = "line_number";
  public static final String METHOD_NAME = "method_name";
  public static final String MESSAGE = "message";
  public static final String NDC = "ndc";
  public static final String APP_START_TIME = "app_start_time";
  public static final String THREAD_NAME = "thread_name";
  public static final String THROWABLE_STR = "throwable_str_rep";
  public static final String TIMESTAMP = "log_timestamp";

  // session state
  private PreparedStatement statement;
  private volatile boolean initialized = false;
  private volatile boolean initializationFailed = false;
  private Cluster cluster;
  private Session session;
  private long appStartTime = ManagementFactory.getRuntimeMXBean().getStartTime();

  protected CassandraAppender(String name, Filter filter, Layout<?> layout, boolean ignoreException) {
    super(name, filter, layout, false);
  }

  @PluginFactory
  public static CassandraAppender createAppender(
      @PluginAttribute("name") String name,
      @PluginAttribute("ignoreExceptions") boolean ignoreException,
      @PluginElement("Layout") Layout<?> layout,
      @PluginElement("Filters") Filter filter) {

    if (name == null) {
      LOGGER.error("No name provided for CassandraAppender");
      return null;
    }
    
    if (layout == null) {
      layout = PatternLayout.createDefaultLayout();
    }
    return new CassandraAppender(name, filter, layout, ignoreException);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void append(LogEvent event)
  {
    // We have to defer initialization of the client because TTransportFactory
    // references some Hadoop classes which can't safely be used until the logging
    // infrastructure is fully set up. If we attempt to initialize the client
    // earlier, it causes NPE's from the constructor of org.apache.hadoop.conf.Configuration.
    if (!initialized)
      initClient();
    if (!initializationFailed)
      createAndExecuteQuery(event);
  }

  //Connect to cassandra, then setup the schema and preprocessed statement
  private synchronized void initClient()
  {
    // We should be able to go without an Atomic variable here.  There are two potential problems:
    // 1. Multiple threads read intialized=false and call init client.  However, the method is
    //    synchronized so only one will get the lock first, and the others will drop out here.
    // 2. One thread reads initialized=true before initClient finishes.  This also should not
    //    happen as the lock should include a memory barrier.
    if (initialized || initializationFailed)
      return;

    // Just while we initialise the client, we must temporarily
    // disable all logging or else we get into an infinite loop
    LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    org.apache.logging.log4j.core.config.Configuration config = ctx.getConfiguration();
    LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME); 
    Level globalThreshold = loggerConfig.getLevel();
    loggerConfig.setLevel(Level.OFF);
    ctx.updateLoggers();  

    try
    {
      Cluster.Builder builder = Cluster.builder()
          .addContactPoints(hosts.split(",\\s*"))
          .withPort(port)
          .withLoadBalancingPolicy(new RoundRobinPolicy());

      // Kerberos provides authentication anyway, so a username and password are superfluous.  SSL
      // is compatible with either.
      boolean passwordAuthentication = !password.equals("") || !username.equals("");
      if (authProviderOptions != null && passwordAuthentication)
        throw new IllegalArgumentException(
            "Authentication via both Cassandra usernames and Kerberos " +
                "requested.");

      // Encryption
      if (authProviderOptions != null)
        builder = builder.withAuthProvider(getAuthProvider());
      if (sslOptions != null)
        builder = builder.withSSL(getSslOptions());
      if (passwordAuthentication)
        builder = builder.withCredentials(username, password);

      cluster = builder.build();
      session = cluster.connect();
      setupSchema();
      setupStatement();
    } catch (Exception e)
    {
      LOGGER.error("Error ", e);

      //If the user misconfigures the port or something, don't keep failing.
      initializationFailed = true;
    } finally
    {
      //Always reenable logging
      loggerConfig.setLevel(globalThreshold);
      ctx.updateLoggers();

      initialized = true;
    }
  }

  /**
   * Create Keyspace and CF if they do not exist.
   */
  private void setupSchema() throws IOException
  {
    //Create keyspace if necessary
    String ksQuery = String.format("CREATE KEYSPACE IF NOT EXISTS \"%s\" WITH REPLICATION = %s;",
        keyspaceName, replication);
    session.execute(ksQuery);

    //Create table if necessary
    String cfQuery =
        String.format("CREATE TABLE IF NOT EXISTS \"%s\".\"%s\" (%s UUID PRIMARY KEY, " +
            "%s text, %s bigint, %s text, %s text, %s text, %s text, %s text," +
            "%s text, %s text, %s bigint, %s text, %s text, %s text, %s text," +
            "%s text);",
            keyspaceName, columnFamily, ID, APP_NAME, APP_START_TIME, CLASS_NAME,
            FILE_NAME, HOST_IP, HOST_NAME, LEVEL, LINE_NUMBER, METHOD_NAME,
            TIMESTAMP, LOGGER_NAME, MESSAGE, NDC, THREAD_NAME, THROWABLE_STR);
    session.execute(cfQuery);
  }

  /**
   * Setup and preprocess our insert query, so that we can just bind values and send them over the binary protocol
   */
  private void setupStatement()
  {
    //Preprocess our append statement
    String insertQuery = String.format("INSERT INTO \"%s\".\"%s\" " +
        "(%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?); ",
        keyspaceName, columnFamily, ID, APP_NAME, HOST_IP, HOST_NAME, LOGGER_NAME,
        LEVEL, CLASS_NAME, FILE_NAME, LINE_NUMBER, METHOD_NAME, MESSAGE, NDC,
        APP_START_TIME, THREAD_NAME, THROWABLE_STR, TIMESTAMP);

    statement = session.prepare(insertQuery);
    statement.setConsistencyLevel(ConsistencyLevel.valueOf(consistencyLevelWrite.toString()));
  }

  /**
   * Send one logging event to Cassandra.  We just bind the new values into the preprocessed query
   * built by setupStatement
   */
  private void createAndExecuteQuery(LogEvent event)
  {
    BoundStatement bound = new BoundStatement(statement);

    // A primary key combination of timestamp/hostname/threadname should be unique as long as the thread names
    // are set, but would not be backwards compatible.  Do we care?
    bound.setUUID(0, UUID.randomUUID());

    bound.setString(1, appName);
    bound.setString(2, ip);
    bound.setString(3, hostname);
    bound.setString(4, event.getLoggerName());
    bound.setString(5, event.getLevel().toString());

    StackTraceElement locInfo = event.getSource();
    if (locInfo != null) {
      bound.setString(6, locInfo.getClassName());
      bound.setString(7, locInfo.getFileName());
      bound.setString(8, String.valueOf(locInfo.getLineNumber()));
      bound.setString(9, locInfo.getMethodName());
    }

    bound.setString(10, event.getMessage().getFormattedMessage());
    bound.setString(11, event.getContextStack().toString());
    bound.setLong(12, appStartTime);
    bound.setString(13, event.getThreadName());
    Throwable throwable = event.getThrown();
    bound.setString(14, throwable == null ? null : throwable.toString());
    bound.setLong(15, event.getTimeMillis());
    session.execute(bound);
  }

  /**
   * {@inheritDoc}
   */
  public void close()
  {
    session.closeAsync();
    cluster.closeAsync();
  }

  /**
   * {@inheritDoc}
   *
   * @see org.apache.log4j.Appender#requiresLayout()
   */
  public boolean requiresLayout()
  {
    return false;
  }

  /**
   * Called once all the options have been set. Starts listening for clients
   * on the specified socket.
   */
  public void activateOptions()
  {
    // reset();
  }

  //
  //Boilerplate from here on out
  //

  public String getKeyspaceName()
  {
    return keyspaceName;
  }

  public void setKeyspaceName(String keyspaceName)
  {
    this.keyspaceName = keyspaceName;
  }

  public String getHosts()
  {
    return hosts;
  }

  public void setHosts(String hosts)
  {
    this.hosts = hosts;
  }

  public int getPort()
  {
    return port;
  }

  public void setPort(int port)
  {
    this.port = port;
  }

  public String getUsername()
  {
    return username;
  }

  public void setUsername(String username)
  {
    this.username = unescape(username);
  }

  public String getPassword()
  {
    return password;
  }

  public void setPassword(String password)
  {
    this.password = unescape(password);
  }

  public String getColumnFamily()
  {
    return columnFamily;
  }

  public void setColumnFamily(String columnFamily)
  {
    this.columnFamily = columnFamily;
  }

  public String getReplication()
  {
    return replication;
  }

  public void setReplication(String strategy)
  {
    replication = unescape(strategy);
  }

  private Map<String, String> parseJsonMap(String options, String type) throws Exception
  {
    if (options == null)
      throw new IllegalArgumentException(type + "Options can't be null.");

    return jsonMapper.readValue(unescape(options), new TreeMap<String, String>().getClass());
  }

  public void setAuthProviderOptions(String newOptions) throws Exception
  {
    authProviderOptions = parseJsonMap(newOptions, "authProvider");
  }

  public void setSslOptions(String newOptions) throws Exception
  {
    sslOptions = parseJsonMap(newOptions, "Ssl");
  }

  public String getConsistencyLevelWrite()
  {
    return consistencyLevelWrite.toString();
  }

  public void setConsistencyLevelWrite(String consistencyLevelWrite)
  {
    try {
      this.consistencyLevelWrite = ConsistencyLevel.valueOf(unescape(consistencyLevelWrite));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Consistency level " + consistencyLevelWrite
          + " wasn't found. Available levels: " + Joiner.on(", ").join(ConsistencyLevel.values()));
    }
  }

  public String getAppName()
  {
    return appName;
  }

  public void setAppName(String appName)
  {
    this.appName = appName;
  }

  private static String getHostName()
  {
    String hostname = "unknown";

    try {
      InetAddress addr = InetAddress.getLocalHost();
      hostname = addr.getHostName();
    } catch (Throwable t) {

    }
    return hostname;
  }

  private static String getIP()
  {
    String ip = "unknown";

    try {
      InetAddress addr = InetAddress.getLocalHost();
      ip = addr.getHostAddress();
    } catch (Throwable t) {

    }
    return ip;
  }

  /**
   * Strips leading and trailing '"' characters
   * 
   * @param b
   *            - string to unescape
   * @return String - unexspaced string
   */
  private static String unescape(String b)
  {
    if (b.charAt(0) == '\"' && b.charAt(b.length() - 1) == '\"')
      b = b.substring(1, b.length() - 1);
    return b;
  }

  // Create an SSLContext (a container for a keystore and a truststore and their associated options)
  // Assumes sslOptions map is not null
  private SSLOptions getSslOptions() throws Exception
  {
    // init trust store
    TrustManagerFactory tmf = null;
    String truststorePath = sslOptions.get("ssl.truststore");
    String truststorePassword = sslOptions.get("ssl.truststore.password");
    if (truststorePath != null && truststorePassword != null)
    {
      FileInputStream tsf = new FileInputStream(truststorePath);
      KeyStore ts = KeyStore.getInstance("JKS");
      ts.load(tsf, truststorePassword.toCharArray());
      tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmf.init(ts);
    }

    // init key store
    KeyManagerFactory kmf = null;
    String keystorePath = sslOptions.get("ssl.keystore");
    String keystorePassword = sslOptions.get("ssl.keystore.password");
    if (keystorePath != null && keystorePassword != null)
    {
      FileInputStream ksf = new FileInputStream(keystorePath);
      KeyStore ks = KeyStore.getInstance("JKS");
      ks.load(ksf, keystorePassword.toCharArray());
      kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      kmf.init(ks, keystorePassword.toCharArray());

    }

    // init cipher suites
    String[] ciphers = SSLOptions.DEFAULT_SSL_CIPHER_SUITES;
    if (sslOptions.containsKey("ssl.ciphersuites"))
      ciphers = sslOptions.get("ssl.ciphersuits").split(",\\s*");

    SSLContext ctx = SSLContext.getInstance("SSL");
    ctx.init(kmf == null ? null : kmf.getKeyManagers(),
        tmf == null ? null : tmf.getTrustManagers(),
        new SecureRandom());

    return new SSLOptions(ctx, ciphers);
  }

  // Load a custom AuthProvider class dynamically.
  public AuthProvider getAuthProvider() throws Exception
  {
    ClassLoader cl = ClassLoader.getSystemClassLoader();

    if (!authProviderOptions.containsKey("auth.class"))
      throw new IllegalArgumentException("authProvider map does not include auth.class.");
    Class<?> dap = cl.loadClass(authProviderOptions.get("auth.class"));

    // Perhaps this should be a factory, but it seems easy enough to just have a single string parameter
    // which can be encoded however, e.g. another JSON map
    if (authProviderOptions.containsKey("auth.options"))
      return (AuthProvider) dap.getConstructor(String.class).newInstance(
          authProviderOptions.get("auth.options"));
    else
      return (AuthProvider) dap.newInstance();
  }

}
