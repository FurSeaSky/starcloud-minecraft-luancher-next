package cn.starcloud.core.auth.exception;

/**
 * Control-flow signal used inside a login flow to unwind to its single catch block.
 *
 * <p>The auth steps are deep chains of private methods that all have to give up as soon as one step
 * fails. Throwing this (rather than returning {@code null} and checking at every level) keeps the
 * happy path linear. It is always caught inside the flow and never reaches a caller; treat it as an
 * implementation detail of {@link cn.starcloud.core.auth.auths.MicrosoftAuth}.
 */
public class AbortFlow extends RuntimeException {

  /**
   * Creates the signal describing where the flow was abandoned.
   *
   * @param message description of the abort point
   */
  public AbortFlow(String message) {
    super(message);
  }

  /**
   * Creates the signal carrying the error that caused the abort.
   *
   * @param message description of the abort point
   * @param cause the underlying failure
   */
  public AbortFlow(String message, Throwable cause) {
    super(message, cause);
  }

  /** Creates the signal without a message or cause. */
  public AbortFlow() {
    super();
  }
}
