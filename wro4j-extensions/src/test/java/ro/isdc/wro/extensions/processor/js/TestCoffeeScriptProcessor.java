package ro.isdc.wro.extensions.processor.js;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.Reader;
import java.io.Writer;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import ro.isdc.wro.config.Context;
import ro.isdc.wro.model.resource.Resource;
import ro.isdc.wro.model.resource.ResourceType;
import ro.isdc.wro.model.resource.processor.ResourcePreProcessor;
import ro.isdc.wro.model.resource.processor.decorator.LazyProcessorDecorator;
import ro.isdc.wro.util.LazyInitializer;
import ro.isdc.wro.util.WroTestUtils;


/**
 * @author Alex Objelean
 */
public class TestCoffeeScriptProcessor {
  @Mock
  private Resource mockResource;
  @Mock
  private Reader mockReader;
  @Mock
  private Writer mockWriter;
  @Mock
  private NodeCoffeeScriptProcessor mockNodeProcessor;
  @Mock
  private ResourcePreProcessor mockFallbackProcessor;
  private ResourcePreProcessor victim;

  @BeforeClass
  public static void onBeforeClass() {
    assertEquals(0, Context.countActive());
  }

  @AfterClass
  public static void onAfterClass() {
    assertEquals(0, Context.countActive());
  }

  @Before
  public void setUp() {
    Context.set(Context.standaloneContext());
    MockitoAnnotations.initMocks(this);
    // use lazy initialization to defer constructor invocation
    victim = new LazyProcessorDecorator(new LazyInitializer<ResourcePreProcessor>() {
      @Override
      protected ResourcePreProcessor initialize() {
        return new CoffeeScriptProcessor() {

          @Override
          protected ResourcePreProcessor createFallbackProcessor() {
            return mockFallbackProcessor;
          }

          @Override
          protected NodeCoffeeScriptProcessor createNodeProcessor() {
            return mockNodeProcessor;
          }
        };
      }
    });
    WroTestUtils.createInjector().inject(victim);
  }

  @After
  public void tearDown() {
    Context.unset();
  }

  @Test
  public void shouldUseNodeProcessorWhenSupported()
      throws Exception {
    when(mockNodeProcessor.isSupported()).thenReturn(true);
    victim.process(mockResource, mockReader, mockWriter);
    verify(mockNodeProcessor, Mockito.times(1)).process(mockResource, mockReader, mockWriter);
    verify(mockFallbackProcessor, Mockito.never()).process(mockResource, mockReader, mockWriter);
  }

  @Test
  public void shouldUseFallbackProcessorWhenNodeNotSupported()
      throws Exception {
    when(mockNodeProcessor.isSupported()).thenReturn(false);
    victim.process(mockResource, mockReader, mockWriter);
    verify(mockNodeProcessor, Mockito.never()).process(mockResource, mockReader, mockWriter);
    verify(mockFallbackProcessor, Mockito.times(1)).process(mockResource, mockReader, mockWriter);
  }

  @Test
  public void shouldSupportCorrectResourceTypes() {
    WroTestUtils.assertProcessorSupportResourceTypes(new CoffeeScriptProcessor(), ResourceType.JS);
  }
}
