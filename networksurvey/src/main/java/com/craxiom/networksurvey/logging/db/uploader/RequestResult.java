/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.craxiom.networksurvey.logging.db.uploader;

public enum RequestResult
{
    Success, Failure, ConfigurationError, InvalidApiKey, ConnectionError, ServerError, LimitExceeded
}
