/*
 ************************************************************************
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 *
 * (c) 2012.                         (c) 2012.
 * National Research Council            Conseil national de recherches
 * Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
 * All rights reserved                  Tous droits reserves
 *
 * NRC disclaims any warranties         Le CNRC denie toute garantie
 * expressed, implied, or statu-        enoncee, implicite ou legale,
 * tory, of any kind with respect       de quelque nature que se soit,
 * to the software, including           concernant le logiciel, y com-
 * without limitation any war-          pris sans restriction toute
 * ranty of merchantability or          garantie de valeur marchande
 * fitness for a particular pur-        ou de pertinence pour un usage
 * pose.  NRC shall not be liable       particulier.  Le CNRC ne
 * in any event for any damages,        pourra en aucun cas etre tenu
 * whether direct or indirect,          responsable de tout dommage,
 * special or general, consequen-       direct ou indirect, particul-
 * tial or incidental, arising          ier ou general, accessoire ou
 * from the use of the software.        fortuit, resultant de l'utili-
 *                                      sation du logiciel.
 *
 *
 * @author jenkinsd
 * 10/17/12 - 1:13 PM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.vos.client.ui;

import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.client.VOSpaceClient;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * The main manager to manage the logic of the uploads.  Reports to UI elements
 * via the listeners.
 */
public class UploadManagerImpl implements UploadManager
{
    // If we don't use a different logger here, then it will try to log to the
    // AWT console outside the EDT.
    private static final Logger LOGGER =
            Logger.getLogger(UploadManagerImpl.class);
    private static final int MAX_COMMAND_COUNT = 500;

    private final File sourceDirectory;
    private final VOSURI targetVOSpaceURI;
    private final VOSpaceClient voSpaceClient;

    private boolean abortIssued;

    private ExecutorService commandController;
    private final CommandQueue commandQueue;

    private final List<CommandQueueListener> commandQueueListeners =
            new ArrayList<CommandQueueListener>();


    /**
     * Only available constructor.  Complete.
     *
     * @param sourceDirectory       The Source directory to upload.
     * @param targetVOSpaceURI      The URI of the target VOSpace.
     * @param vospaceClient         The VOSpace client instance to use.
     * @param commandQueueListener  The main listener.
     */
    public UploadManagerImpl(final File sourceDirectory,
                             final VOSURI targetVOSpaceURI,
                             final VOSpaceClient vospaceClient,
                             final CommandQueueListener commandQueueListener)
    {
        this.sourceDirectory = sourceDirectory;
        this.targetVOSpaceURI = targetVOSpaceURI;
        this.voSpaceClient = vospaceClient;
        this.commandQueue = new CommandQueue(MAX_COMMAND_COUNT,
                                             commandQueueListener);
    }


    /**
     * Begin the UploadManager's Producer and Consumer threads.
     */
    @Override
    public void start()
    {
        LOGGER.info("Starting process.");
        initializeCommandController();
    }

    /**
     * Create the command controller and set it.
     */
    protected void initializeCommandController()
    {
        setCommandController(Executors.newFixedThreadPool(2));

        getCommandController().execute(
                new CommandExecutor(getVOSpaceClient(), getCommandQueue()));
        getCommandController().submit(
                new FileSystemScanner(getSourceDirectory(),
                                      getTargetVOSpaceURI(),
                                      getCommandQueue()));

        // Ensure the producer/consumer thread counts do not grow.  Shutdown
        // after the queue is done.
        getCommandController().shutdown();
    }

    /**
     * Abort the process(es) while they're working.
     */
    @Override
    public void abort()
    {
        LOGGER.info("Abort issued.");
        abortIssued = true;

        getCommandQueue().abortProduction();
        getCommandController().shutdownNow();
    }

    /**
     * Shutdown the Manager.  This is a hard stop issued after completion.
     */
    @Override
    public void stop()
    {
        LOGGER.info("Full stop.");
        abortIssued = false;

        getCommandController().shutdownNow();
    }

    /**
     * Obtain whether an Abort was issued.
     *
     * @return True if aborted, False otherwise.
     */
    @Override
    public boolean isAbortIssued()
    {
        return abortIssued;
    }

    public File getSourceDirectory()
    {
        return sourceDirectory;
    }

    public VOSURI getTargetVOSpaceURI()
    {
        return targetVOSpaceURI;
    }

    public VOSpaceClient getVOSpaceClient()
    {
        return voSpaceClient;
    }

    public ExecutorService getCommandController()
    {
        return commandController;
    }

    public void setCommandController(ExecutorService commandController)
    {
        this.commandController = commandController;
    }

    public CommandQueue getCommandQueue()
    {
        return commandQueue;
    }

    public void registerCommandQueueListener(
            final CommandQueueListener commandQueueListener)
    {
        getCommandQueueListeners().add(commandQueueListener);
    }

    public List<CommandQueueListener> getCommandQueueListeners()
    {
        return commandQueueListeners;
    }

}