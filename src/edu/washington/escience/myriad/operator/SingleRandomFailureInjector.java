package edu.washington.escience.myriad.operator;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.ImmutableMap;

import edu.washington.escience.myriad.DbException;
import edu.washington.escience.myriad.Schema;
import edu.washington.escience.myriad.TupleBatch;

/**
 * Inject a random failure. The injection is conducted as the following:<br/>
 * <ul>
 * <li>At the time the operator is opened, the delay starts to count.</li>
 * <li>Do nothing before delay expires.</li>
 * <li>After delay expires, for each second, randomly decide if a failure should be injected, i.e. throw an
 * {@link InjectedFailureException}, according to the failure probability.</li>
 * </ul>
 * */
public class SingleRandomFailureInjector extends Operator {

  /**
   * Logger.
   * */
  private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(SingleRandomFailureInjector.class
      .getName());

  /**
   * Child.
   * */
  private Operator child;

  /**
   * failure probability per second.
   * */
  private final double failureProbabilityPerSecond;

  /**
   * Record if a failure is already injected. The class injects only a single failure.
   * */
  private volatile boolean hasFailed;

  /**
   * Denoting if the next tuple retrieval event should trigger a failure.
   * */
  private volatile boolean toFail;

  /**
   * failure inject thread. This thread decides if a failure should be injected.
   * */
  private volatile Thread failureInjectThread;

  /**
   * delay in milliseconds.
   * */
  private final long delayInMS;

  /**
   * @param delay the delay
   * @param delayUnit the timeunit of the delay
   * @param failureProbabilityPerSecond per second failure probability.
   * @param child the child operator.
   * */
  public SingleRandomFailureInjector(final long delay, final TimeUnit delayUnit,
      final double failureProbabilityPerSecond, final Operator child) {
    this.child = child;
    this.failureProbabilityPerSecond = failureProbabilityPerSecond;
    hasFailed = false;
    toFail = false;
    delayInMS = delayUnit.toMillis(delay);
  }

  /**
   * 
   */
  private static final long serialVersionUID = 1L;

  @Override
  protected final TupleBatch fetchNext() throws DbException, InterruptedException {
    if (toFail) {
      toFail = false;
      throw new InjectedFailureException("Failure injected by " + this);
    }
    return child.next();
  }

  @Override
  public final Operator[] getChildren() {
    return new Operator[] { child };
  }

  @Override
  protected final void init(final ImmutableMap<String, Object> initProperties) throws DbException {
    toFail = false;
    if (!hasFailed) {
      failureInjectThread = new Thread() {
        @Override
        public void run() {
          Random r = new Random();
          try {
            Thread.sleep(delayInMS);
          } catch (InterruptedException e) {
            if (LOGGER.isDebugEnabled()) {
              LOGGER.debug(SingleRandomFailureInjector.this + " exit during delay.");
            }
            return;
          }
          while (true) {
            try {
              Thread.sleep(TimeUnit.SECONDS.toMillis(1));
            } catch (InterruptedException e) {
              if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(SingleRandomFailureInjector.this + " exit during 1 second sleep.");
              }
              return;
            }
            if (r.nextDouble() < failureProbabilityPerSecond) {
              toFail = true;
              hasFailed = true;
              return;
            }
          }
        }
      };
      failureInjectThread.start();
    } else {
      failureInjectThread = null;
    }

  }

  @Override
  protected final void cleanup() throws DbException {
    if (failureInjectThread != null) {
      failureInjectThread.interrupt();
      failureInjectThread = null;
    }
    toFail = false;
  }

  @Override
  protected final TupleBatch fetchNextReady() throws DbException {
    if (toFail) {
      toFail = false;
      throw new InjectedFailureException("Failure injected by " + this);
    }
    return child.nextReady();
  }

  @Override
  public final Schema getSchema() {
    return child.getSchema();
  }

  @Override
  public final void setChildren(final Operator[] children) {
    child = children[0];
  }

}
