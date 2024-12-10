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

import org.hammock.sync.internal.android.Base64InputStreamFactory;
import org.hammock.sync.internal.documentstore.AttachmentManager;
import org.hammock.sync.internal.documentstore.AttachmentStreamFactory;
import org.hammock.sync.internal.documentstore.DatabaseImpl;
import org.hammock.sync.documentstore.DocumentNotFoundException;
import org.hammock.sync.internal.documentstore.InternalDocumentRevision;
import org.hammock.sync.internal.documentstore.ForceInsertItem;
import org.hammock.sync.internal.documentstore.PreparedAttachment;
import org.hammock.sync.documentstore.UnsavedStreamAttachment;
import org.hammock.sync.event.notifications.DocumentCreated;
import org.hammock.sync.event.notifications.DocumentModified;
import org.hammock.sync.event.notifications.DocumentUpdated;
import org.hammock.sync.internal.sqlite.SQLCallable;
import org.hammock.sync.internal.sqlite.SQLDatabase;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Force insert a list of items (Revisions) obtained by pull Replication into the local database
 */
public class ForceInsertCallable implements SQLCallable<List<DocumentModified>> {

    private static final Logger logger = Logger.getLogger(DatabaseImpl.class.getCanonicalName());

    private List<ForceInsertItem> items;

    private String attachmentsDir;
    private AttachmentStreamFactory attachmentStreamFactory;

    public ForceInsertCallable(List<ForceInsertItem> items, String attachmentsDir,
                               AttachmentStreamFactory attachmentStreamFactory) {
        this.items = items;
        this.attachmentsDir = attachmentsDir;
        this.attachmentStreamFactory = attachmentStreamFactory;
    }

    @Override
    public List<DocumentModified> call(SQLDatabase db) throws Exception {
    List<DocumentModified> events = new ArrayList<DocumentModified>();
    
    for (ForceInsertItem item : items) {
        logger.finer("forceInsert(): " + item.rev.toString());
        DocumentModified event = processForceInsertItem(db, item);
        if (event != null) {
            events.add(event);
            logger.log(Level.FINER, "Inserted revision: %s", item.rev);
        }
    }
    return events;
}

private DocumentModified processForceInsertItem(SQLDatabase db, ForceInsertItem item) throws Exception {
    long docNumericId = new GetNumericIdCallable(item.rev.getId()).call(db);
    DocumentModified event = insertDocument(db, item, docNumericId);
    handleAttachments(db, item);
    return event;
}

private DocumentModified insertDocument(SQLDatabase db, ForceInsertItem item, long docNumericId) throws Exception {
    long seq;
    if (docNumericId != -1) {
        seq = new DoForceInsertExistingDocumentWithHistoryCallable(item.rev,
                docNumericId, item.revisionHistory,
                item.attachments, attachmentsDir, attachmentStreamFactory).call(db);
        item.rev.initialiseSequence(seq);
        return new DocumentUpdated(null, item.rev);
    } else {
        seq = new DoForceInsertNewDocumentWithHistoryCallable(item.rev, item.revisionHistory).call(db);
        item.rev.initialiseSequence(seq);
        return new DocumentCreated(item.rev);
    }
}

private void handleAttachments(SQLDatabase db, ForceInsertItem item) throws Exception {
    if (item.pullAttachmentsInline) {
        processInlineAttachments(db, item);
    } else {
        processPreparedAttachments(db, item);
    }
}

private void processInlineAttachments(SQLDatabase db, ForceInsertItem item) throws Exception {
    if (item.attachments != null) {
        for (String att : item.attachments.keySet()) {
            Map attachmentMetadata = (Map) item.attachments.get(att);
            if (isAttachmentStub(attachmentMetadata)) {
                continue;
            }
            addInlineAttachment(db, item, att, attachmentMetadata);
        }
    }
}

private boolean isAttachmentStub(Map attachmentMetadata) {
    Boolean stub = (Boolean) attachmentMetadata.get("stub");
    return stub != null && stub;
}

private void addInlineAttachment(SQLDatabase db, ForceInsertItem item, String att, Map attachmentMetadata) throws Exception {
    String data = (String) attachmentMetadata.get("data");
    String type = (String) attachmentMetadata.get("content_type");
    InputStream is = Base64InputStreamFactory.get(new ByteArrayInputStream(data.getBytes("UTF-8")));
    UnsavedStreamAttachment usa = new UnsavedStreamAttachment(is, type);
    try {
        PreparedAttachment pa = AttachmentManager.prepareAttachment(attachmentsDir, attachmentStreamFactory, usa);
        AttachmentManager.addAttachment(db, attachmentsDir, item.rev, pa, att);
    } catch (Exception e) {
        logger.log(Level.SEVERE, "There was a problem adding the attachment " + usa + "to the datastore for document " + item.rev, e);
        throw e;
    }
}

private void processPreparedAttachments(SQLDatabase db, ForceInsertItem item) throws Exception {
    try {
        if (item.preparedAttachments != null) {
            for (String[] key : item.preparedAttachments.keySet()) {
                addPreparedAttachment(db, item, key);
            }
        }
    } catch (Exception e) {
        logger.log(Level.SEVERE, "There was a problem adding an attachment to the datastore", e);
        throw e;
    }
}

private void addPreparedAttachment(SQLDatabase db, ForceInsertItem item, String[] key) throws Exception {
    String id = key[0];
    String rev = key[1];
    try {
        InternalDocumentRevision doc = new GetDocumentCallable(id, rev, attachmentsDir, attachmentStreamFactory).call(db);
        AttachmentManager.addAttachmentsToRevision(db, attachmentsDir, doc, item.preparedAttachments.get(key));
    } catch (DocumentNotFoundException e) {
        //safe to continue, previously getDocumentInQueue could return null and this was deemed safe and expected behaviour
        //DocumentNotFoundException is thrown instead of returning null now.
    }
}

//Refactoring end
}
