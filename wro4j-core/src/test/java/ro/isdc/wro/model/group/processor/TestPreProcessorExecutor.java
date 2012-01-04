/**
 * Copyright wro4j@2011
 */
package ro.isdc.wro.model.group.processor;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;

import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.isdc.wro.WroRuntimeException;
import ro.isdc.wro.config.Context;
import ro.isdc.wro.config.jmx.WroConfiguration;
import ro.isdc.wro.manager.factory.BaseWroManagerFactory;
import ro.isdc.wro.manager.factory.WroManagerFactory;
import ro.isdc.wro.model.group.Group;
import ro.isdc.wro.model.resource.Resource;
import ro.isdc.wro.model.resource.ResourceType;
import ro.isdc.wro.model.resource.processor.ResourcePreProcessor;
import ro.isdc.wro.model.resource.processor.ResourceProcessor;
import ro.isdc.wro.model.resource.processor.factory.SimpleProcessorsFactory;
import ro.isdc.wro.util.StopWatch;


/**
 * @author Alex Objelean
 */
public class TestPreProcessorExecutor {
  private static final Logger LOG = LoggerFactory.getLogger(TestPreProcessorExecutor.class);

  private PreProcessorExecutor executor;


  @Before
  public void setUp() {
    Context.set(Context.standaloneContext());
    //force parallel execution
    Context.get().getConfig().setParallelPreprocessing(true);
    initExecutor();
  }


  private WroManagerFactory createWroManager(final ResourceProcessor... preProcessors) {
    final SimpleProcessorsFactory processorsFactory = new SimpleProcessorsFactory();
    for (final ResourceProcessor resourcePreProcessor : preProcessors) {
      processorsFactory.addPreProcessor(resourcePreProcessor);
    }
    final BaseWroManagerFactory wroManagerFactory = new BaseWroManagerFactory();
    wroManagerFactory.setProcessorsFactory(processorsFactory);
    return wroManagerFactory;
  }


  /**
   * @param wroManagerFactory
   */
  private void initExecutor(final ResourceProcessor... preProcessors) {
    final WroManagerFactory wroManagerFactory = createWroManager(preProcessors);
    final Injector injector = new Injector(wroManagerFactory.create());
    executor = new PreProcessorExecutor();
    injector.inject(executor);
  }


  @Test(expected = NullPointerException.class)
  public void cannotAcceptNullArguments()
    throws Exception {
    executor.processAndMerge(null, true);
  }


  /**
   * Creates a slow pre processor which sleeps for a given amount of milliseconds and doesn't change the processed
   * content.
   */
  private ResourcePreProcessor createSlowPreProcessor(final long time) {
    return new ResourcePreProcessor() {
      public void process(final Resource resource, final Reader reader, final Writer writer)
        throws IOException {
        try {
          IOUtils.copy(reader, writer);
          Thread.sleep(time);
        } catch (final InterruptedException e) {
        }
      }
    };
  }


  private ResourcePreProcessor createProcessorUsingMissingResource() {
    return new ResourcePreProcessor() {
      public void process(final Resource resource, final Reader reader, final Writer writer)
        throws IOException {
        LOG.debug("executing processor which will throw IOException");
        throw new IOException("Invalid resource found!");
      }
    };
  }


  private ResourceProcessor createProcessorWhichFails() {
    return new ResourceProcessor() {
      public void process(final Resource resource, final Reader reader, final Writer writer)
        throws IOException {
        LOG.debug("executing failing processor...");
        throw new WroRuntimeException("Boom!");
      }
    };
  }


  @Test
  public void processEmptyList()
    throws Exception {
    final List<Resource> resources = new ArrayList<Resource>();
    Group group = new Group("dummy");
    group.setResources(resources);
    Assert.assertEquals("", executor.processAndMerge(group, true));
    Assert.assertEquals("", executor.processAndMerge(group, false));
  }


  @Test
  public void shouldNotFailWhenNoResourcesProcessed()
    throws Exception {
    initExecutor(createProcessorUsingMissingResource());
    executor.processAndMerge(createGroup(), true);
  }


	private Group createGroup(final Resource... resources) {
		Group group = new Group("dummy");
		final List<Resource> resourcesList = new ArrayList<Resource>();
		for (final Resource resource : resources) {
			resourcesList.add(resource);
		}
		group.setResources(resourcesList);
		return group;
	}


  @Test(expected = IOException.class)
  public void shouldFailWhenProcessingInvalidResource()
    throws Exception {
    Context.get().getConfig().setIgnoreMissingResources(false);
    shouldNotFailWhenProcessingInvalidResource();
  }


  @Test
  public void shouldNotFailWhenProcessingInvalidResource()
    throws IOException {
    initExecutor(createProcessorUsingMissingResource());
    final Group group = createGroup(Resource.create("uri", ResourceType.JS));
    final String result = executor.processAndMerge(group, true);
    Assert.assertEquals("", result);
  }


  @Test(expected = WroRuntimeException.class)
  public void shouldFailWhenUsingFailingPreProcessor()
    throws Exception {
    initExecutor(createProcessorWhichFails());
    final Group group = createGroup(Resource.create("", ResourceType.JS));
    final String result = executor.processAndMerge(group, true);
    Assert.assertEquals("", result);
  }


  /**
   * This test should work when running at least on dual-core.
   * It assumes that (P1(r1) + P2(r1) + P3(r1)) + (P1(r2) + P2(r2) + P3(r2)) > Parallel(P1(r1) + P2(r1) + P3(r1) | P1(r2) + P2(r2) + P3(r2))  
   */
  @Test
  public void preProcessingInParallelIsFaster()
    throws Exception {
    final StopWatch watch = new StopWatch();
    WroConfiguration config = Context.get().getConfig(); 
    watch.start("parallel preProcessing");
    config.setParallelPreprocessing(true);
    initExecutor(createSlowPreProcessor(100), createSlowPreProcessor(100), createSlowPreProcessor(100));
    final Group group = createGroup(Resource.create("r1", ResourceType.JS),
      Resource.create("r2", ResourceType.JS));
    executor.processAndMerge(resources, true);
    watch.stop();
    long parallelExecution = watch.getLastTaskTimeMillis();
    
    config.setParallelPreprocessing(false);
    watch.start("sequential preProcessing");
    executor.processAndMerge(resources, true);
    watch.stop();
    long sequentialExecution = watch.getLastTaskTimeMillis();
    
    String message = "Processing details: \n" + watch.prettyPrint();
    LOG.debug(message);
    
    // prove that running in parallel is faster
    //delta is for executor warm up.
    long delta = 100;
    Assert.assertTrue(sequentialExecution > parallelExecution + delta);
  }


  @After
  public void tearDown() {
    Context.unset();
  }
}