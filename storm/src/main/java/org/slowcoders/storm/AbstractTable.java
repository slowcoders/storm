package org.slowcoders.storm;

import java.sql.SQLException;
import java.util.Collection;

public interface AbstractTable<SNAPSHOT extends EntitySnapshot, REF extends EntityReference, EDITOR extends EntityEditor>
        extends StormRowSet<SNAPSHOT, REF, EDITOR> {

    void deleteEntities(Collection<REF> entities) throws SQLException;

    void updateEntities(ColumnAndValue[] updateValues, Collection<REF> entities) throws SQLException, RuntimeException;

}
