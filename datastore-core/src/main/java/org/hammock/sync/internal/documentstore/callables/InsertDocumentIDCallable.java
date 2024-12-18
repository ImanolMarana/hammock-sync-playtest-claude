/*
 * Copyright © 2016 IBM Corp. All rights reserved.
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

import org.hammock.sync.internal.android.ContentValues;
import org.hammock.sync.documentstore.DocumentStoreException;
import org.hammock.sync.internal.sqlite.SQLCallable;
import org.hammock.sync.internal.sqlite.SQLDatabase;

/**
 * Insert a new Document ID into the @{docs} table. Required when inserting the first Revision.
 */
public class InsertDocumentIDCallable implements SQLCallable<Long> {

    private String docId;

    public InsertDocumentIDCallable(String docId) {
        this.docId = docId;
    }

    @Override
    public Long call(SQLDatabase db) throws DocumentStoreException {
        ContentValues args = new ContentValues();
        args.put("docid", docId);
        long result = db.insert("docs", args);
        if (result == -1) {
            throw new DocumentStoreException("Failed to insert docid "+docId+" into docs table, check log for details");
        }
        return result;
    }
}
