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

import org.hammock.sync.documentstore.Attachment;
import org.hammock.sync.documentstore.AttachmentException;
import org.hammock.sync.internal.documentstore.AttachmentStreamFactory;
import org.hammock.sync.internal.documentstore.DatabaseImpl;
import org.hammock.sync.documentstore.DocumentStoreException;
import org.hammock.sync.internal.documentstore.InternalDocumentRevision;
import org.hammock.sync.internal.documentstore.DocumentRevisionTree;
import org.hammock.sync.internal.documentstore.helpers.GetFullRevisionFromCurrentCursor;
import org.hammock.sync.internal.sqlite.Cursor;
import org.hammock.sync.internal.sqlite.SQLCallable;
import org.hammock.sync.internal.sqlite.SQLDatabase;
import org.hammock.sync.internal.util.DatabaseUtils;

import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Get all Revisions for a given Document ID, in the form of a {@code DocumentRevisionTree}
 *
 * @see DocumentRevisionTree
 */
public class GetAllRevisionsOfDocumentCallable implements SQLCallable<DocumentRevisionTree> {


    private String docId;
    private String attachmentsDir;
    private AttachmentStreamFactory attachmentStreamFactory;

    private static final Logger logger = Logger.getLogger(DatabaseImpl.class.getCanonicalName());

    /**
     * @param docId                   The Document ID to get the Document for
     * @param attachmentsDir          Location of attachments
     * @param attachmentStreamFactory Factory to manage access to attachment streams
     */
    public GetAllRevisionsOfDocumentCallable(String docId, String attachmentsDir,
                                             AttachmentStreamFactory attachmentStreamFactory) {
        this.docId = docId;
        this.attachmentsDir = attachmentsDir;
        this.attachmentStreamFactory = attachmentStreamFactory;
    }

    public DocumentRevisionTree call(SQLDatabase db) throws DocumentStoreException, AttachmentException {
        String sql = "SELECT " + CallableSQLConstants.FULL_DOCUMENT_COLS + " FROM revs, docs " +
                "WHERE docs.docid=? AND revs.doc_id = docs.doc_id ORDER BY sequence ASC";

        String[] args = {docId};
        Cursor cursor = null;

        try {
            DocumentRevisionTree tree = new DocumentRevisionTree();
            cursor = db.rawQuery(sql, args);
            while (cursor.moveToNext()) {
                long sequence = cursor.getLong(3);
                Map<String, ? extends Attachment> atts = new AttachmentsForRevisionCallable(
                        this.attachmentsDir, this.attachmentStreamFactory, sequence).call(db);
                InternalDocumentRevision rev = GetFullRevisionFromCurrentCursor.get(cursor, atts);
                logger.finer("Rev: " + rev);
                tree.add(rev);
            }
            return tree;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error getting all revisions of document", e);
            throw new DocumentStoreException("DocumentRevisionTree not found with id: " + docId, e);
        } finally {
            DatabaseUtils.closeCursorQuietly(cursor);
        }

    }
}
