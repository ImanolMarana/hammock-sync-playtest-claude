/*
 * Copyright © 2016, 2017 IBM Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package org.hammock.sync.internal.documentstore.callables;

import org.hammock.sync.internal.documentstore.DatabaseImpl;
import org.hammock.sync.documentstore.DocumentStoreException;
import org.hammock.sync.internal.documentstore.InternalDocumentRevision;
import org.hammock.sync.internal.documentstore.helpers.InsertStubRevisionAdaptor;
import org.hammock.sync.internal.sqlite.SQLCallable;
import org.hammock.sync.internal.sqlite.SQLDatabase;
import org.hammock.sync.internal.util.Misc;

import java.util.List;
import java.util.logging.Logger;

/**
 * Insert a Document Revision @{code newRevision} into a new Revision tree. Since there is no Revision
 * tree rooted at the oldest Revision in `revisions`, build the initial tree by creating stub
 * Revisions as described by @{code revisions} and make `newRevision` the leaf node of this linear "tree"
 *
 * Note that this is a similar case to @{link DoForceInsertNewDocumentWithHistoryCallable} except
 * there is already a Revision tree for this Document ID (the only material difference between these
 * callables is an extra insert into the `docs` table). Because there is no common ancestor, the
 * result is a "forest" of trees.
 */
public class InsertDocumentHistoryToNewTreeCallable implements SQLCallable<Long> {

    private static final Logger logger = Logger.getLogger(DatabaseImpl.class.getCanonicalName());

    private InternalDocumentRevision newRevision;
    private List<String> revisions;
    private Long docNumericID;

    public InsertDocumentHistoryToNewTreeCallable(InternalDocumentRevision newRevision, List<String>
            revisions, Long docNumericID) {
        this.newRevision = newRevision;
        this.revisions = revisions;
        this.docNumericID = docNumericID;
    }

    @Override
    public Long call(SQLDatabase db) throws DocumentStoreException {
        Misc.checkArgument(DatabaseImpl.checkCurrentRevisionIsInRevisionHistory(newRevision, revisions),
                "Current revision must exist in revision history.");

        // Adding a brand new tree
        logger.finer("Inserting a brand new tree for an existing document.");
        long parentSequence = 0L;
        for (int i = 0; i < revisions.size() - 1; i++) {
            //we copy attachments here so allow the exception to propagate
            parentSequence = InsertStubRevisionAdaptor.insert(docNumericID, revisions.get(i), parentSequence).call(db);
        }
        // don't copy attachments
        String newLeafRev = newRevision.getRevision();
        InsertRevisionCallable callable = new InsertRevisionCallable();
        callable.docNumericId = docNumericID;
        callable.revId = newLeafRev;
        callable.parentSequence = parentSequence;
        callable.deleted = newRevision.isDeleted();
        callable.current = false; // we'll call pickWinnerOfConflicts to set this if it needs it
        callable.data = newRevision.asBytes();
        callable.available = !newRevision.isDeleted();
        long newLeafSeq = callable.call(db);

        new PickWinningRevisionCallable(docNumericID).call(db);
        return newLeafSeq;
    }
}
