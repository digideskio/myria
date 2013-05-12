package edu.washington.escience.myriad;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import com.google.common.base.Preconditions;

import edu.washington.escience.myriad.column.Column;
import edu.washington.escience.myriad.column.ColumnFactory;
import edu.washington.escience.myriad.proto.TransportProto.TransportMessage;
import edu.washington.escience.myriad.util.IPCUtils;

/**
 * Used for creating TupleBatch objects on the fly. A helper class used in, e.g., the Scatter operator.
 * 
 * @author dhalperi
 * 
 */
public class TupleBatchBuffer {
  /** Format of the emitted tuples. */
  private final Schema schema;
  /** Convenience constant; must match schema.numColumns() and currentColumns.size(). */
  private final int numColumns;
  /** List of completed TupleBatch objects. */
  private final List<List<Column<?>>> readyTuples;
  /** Internal state used to build up a TupleBatch. */
  private List<Column<?>> currentColumns;
  /** Internal state representing which columns are ready in the current tuple. */
  private final BitSet columnsReady;
  /** Internal state representing the number of columns that are ready in the current tuple. */
  private int numColumnsReady;
  /** Internal state representing the number of tuples in the in-progress TupleBatch. */
  private int currentInProgressTuples;

  /**
   * Constructs an empty TupleBatchBuffer to hold tuples matching the specified Schema.
   * 
   * @param schema specified the columns of the emitted TupleBatch objects.
   */
  public TupleBatchBuffer(final Schema schema) {
    this.schema = Objects.requireNonNull(schema);
    readyTuples = new LinkedList<List<Column<?>>>();
    currentColumns = ColumnFactory.allocateColumns(schema);
    numColumns = schema.numColumns();
    columnsReady = new BitSet(numColumns);
    numColumnsReady = 0;
    currentInProgressTuples = 0;
  }

  /**
   * clear this TBB.
   * */
  public final void clear() {
    columnsReady.clear();
    currentColumns.clear();
    currentInProgressTuples = 0;
    numColumnsReady = 0;
    readyTuples.clear();
  }

  /**
   * Makes a batch of any tuples in the buffer and appends it to the internal list.
   * 
   * @return true if any tuples were added.
   */
  private boolean finishBatch() {
    if (numColumnsReady != 0) {
      throw new AssertionError("Can't finish a batch with partially-completed tuples!");
    }
    if (currentInProgressTuples == 0) {
      return false;
    }
    // readyTuples.add(new TupleBatch(schema, currentColumns, currentInProgressTuples));
    readyTuples.add(currentColumns);
    currentColumns = ColumnFactory.allocateColumns(schema);
    currentInProgressTuples = 0;
    return true;
  }

  /**
   * Return all tuples in this buffer. The data do not get removed.
   * 
   * @return a List<TupleBatch> containing all complete tuples that have been inserted into this buffer.
   */
  public final List<TupleBatch> getAll() {
    final List<TupleBatch> output = new ArrayList<TupleBatch>();
    for (final List<Column<?>> columns : readyTuples) {
      output.add(new TupleBatch(schema, columns, TupleBatch.BATCH_SIZE));
    }
    if (currentInProgressTuples > 0) {
      output.add(new TupleBatch(schema, currentColumns, currentInProgressTuples));
    }
    return output;
  }

  /**
   * Return all tuples in this buffer. The data do not get removed.
   * 
   * @return a List<TupleBatch> containing all complete tuples that have been inserted into this buffer.
   */
  public final List<List<Column<?>>> getAllAsRawColumn() {
    final List<List<Column<?>>> output = new ArrayList<List<Column<?>>>();
    for (final List<Column<?>> columns : readyTuples) {
      output.add(columns);
    }
    if (currentInProgressTuples > 0) {
      output.add(currentColumns);
    }
    return output;
  }

  /**
   * Return all tuples in this buffer. The data do not get removed.
   * 
   * @return a List<TupleBatch> containing all complete tuples that have been inserted into this buffer.
   */
  public final List<TransportMessage> getAllAsTM() {
    final List<TransportMessage> output = new ArrayList<TransportMessage>();
    if (numTuples() > 0) {
      for (final List<Column<?>> columns : readyTuples) {
        output.add(IPCUtils.normalDataMessage(columns, TupleBatch.BATCH_SIZE));
      }
      if (currentInProgressTuples > 0) {
        output.add(IPCUtils.normalDataMessage(currentColumns, currentInProgressTuples));
      }
    }
    return output;
  }

  /**
   * @return the Schema of the tuples in this buffer.
   */
  public final Schema getSchema() {
    return schema;
  }

  /**
   * @return if there is filled TupleBatches ready for pop.
   * */
  public final boolean hasFilledTB() {
    return readyTuples.size() > 0;
  }

  /**
   * @param another TBB.
   * */
  public final void merge(final TupleBatchBuffer another) {
    readyTuples.addAll(another.readyTuples);
    if (another.currentInProgressTuples > 0) {
      for (int row = 0; row < another.currentInProgressTuples; row++) {
        int column = 0;
        for (final Column<?> c : another.currentColumns) {
          put(column, c.get(row));
          column++;
        }
      }
    }
  }

  /**
   * @return the number of complete tuples stored in this TupleBatchBuffer.
   */
  public final int numTuples() {
    return readyTuples.size() * TupleBatch.BATCH_SIZE + currentInProgressTuples;
  }

  /**
   * @param colIndex column index
   * @param rowIndex row index
   * @return the element at ( rowIndex, colIndex)
   * @throws IndexOutOfBoundsException if indices are out of bounds.
   * */
  public final Object get(final int colIndex, final int rowIndex) throws IndexOutOfBoundsException {
    int tupleBatchIndex = rowIndex / TupleBatch.BATCH_SIZE;
    int tupleIndex = rowIndex % TupleBatch.BATCH_SIZE;
    if (tupleBatchIndex > readyTuples.size() || tupleBatchIndex == readyTuples.size()
        && tupleIndex >= currentInProgressTuples) {
      throw new IndexOutOfBoundsException();
    }
    if (tupleBatchIndex < readyTuples.size()) {
      return readyTuples.get(tupleBatchIndex).get(colIndex).get(tupleIndex);
    }
    return currentColumns.get(colIndex).get(tupleIndex);

  }

  /**
   * @return num columns.
   * */
  public final int numColumns() {
    return numColumns;
  }

  /**
   * @return pop filled and non-filled TupleBatch
   * */
  public final TupleBatch popAny() {
    final TupleBatch tb = popFilled();
    if (tb != null) {
      return tb;
    } else {
      if (currentInProgressTuples > 0) {
        final int size = currentInProgressTuples;
        finishBatch();
        return new TupleBatch(schema, readyTuples.remove(0), size);
      } else {
        return null;
      }
    }
  }

  /**
   * @return pop filled or non-filled as list of columns.
   * */
  public final List<Column<?>> popAnyAsRawColumn() {
    final List<Column<?>> rc = popFilledAsRawColumn();
    if (rc != null) {
      return rc;
    } else {
      if (currentInProgressTuples > 0) {
        finishBatch();
        return readyTuples.remove(0);
      } else {
        return null;
      }
    }
  }

  /**
   * @return pop filled and non-filled TransportMessage
   * */
  public final TransportMessage popAnyAsTM() {
    final TransportMessage ans = popFilledAsTM();
    if (ans != null) {
      return ans;
    } else {
      if (currentInProgressTuples > 0) {
        int numTuples = currentInProgressTuples;
        finishBatch();
        final List<Column<?>> columns = readyTuples.remove(0);
        return IPCUtils.normalDataMessage(columns, numTuples);
      } else {
        return null;
      }
    }
  }

  /**
   * Extract and return the first complete TupleBatch in this Buffer.
   * 
   * @return the first complete TupleBatch in this buffer, or null if none is ready.
   */
  public final TupleBatch popFilled() {
    if (readyTuples.size() > 0) {
      return new TupleBatch(schema, readyTuples.remove(0), TupleBatch.BATCH_SIZE);
    }
    return null;
  }

  /**
   * Pop filled as list of columns. Avoid the overhead of creating TupleBatch instances if needed such as in many tests.
   * 
   * @return list of columns popped or null if no filled tuples ready yet.
   * */
  public final List<Column<?>> popFilledAsRawColumn() {
    if (readyTuples.size() > 0) {
      return readyTuples.remove(0);
    }
    return null;
  }

  /**
   * Pop filled as TransportMessage. Avoid the overhead of creating TupleBatch instances if the data in this TBB are to
   * be sent to other workers.
   * 
   * @return TransportMessage popped or null if no filled tuples ready yet.
   * */
  public final TransportMessage popFilledAsTM() {
    if (readyTuples.size() > 0) {
      final List<Column<?>> columns = readyTuples.remove(0);
      return IPCUtils.normalDataMessage(columns, TupleBatch.BATCH_SIZE);
    }
    return null;
  }

  /**
   * Append the specified value to the specified column.
   * 
   * @param column index of the column.
   * @param value value to be appended.
   */
  public final void put(final int column, final Object value) {
    Preconditions.checkElementIndex(column, numColumns);
    if (columnsReady.get(column)) {
      throw new RuntimeException("Need to fill up one row of TupleBatchBuffer before starting new one");
    }
    currentColumns.get(column).putObject(value);
    columnsReady.set(column, true);
    numColumnsReady++;
    if (numColumnsReady == numColumns) {
      currentInProgressTuples++;
      numColumnsReady = 0;
      columnsReady.clear();
      if (currentInProgressTuples == TupleBatch.BATCH_SIZE) {
        finishBatch();
      }
    }
  }

}
