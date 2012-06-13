/*
 * EventReceiver.java
 * 
 * Copyright (C) 2011 IBR, TU Braunschweig
 *
 * Written-by: Johannes Morgenroth <morgenroth@ibr.cs.tu-bs.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package de.tubs.ibr.dtn.dtalkie.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class EventReceiver extends BroadcastReceiver {
	
	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
		
		Log.d("EventReceiver", "Intent received: " + action);
		
		if (action.equals(de.tubs.ibr.dtn.Intent.STATE))
		{
			String state = intent.getStringExtra("state");
			Log.d("EventReceiver", "State: " + state);
			if (state.equals("ONLINE"))
			{
				// open activity
				Intent i = new Intent(context, DTalkieService.class);
				i.setAction(de.tubs.ibr.dtn.Intent.RECEIVE);
				context.startService(i);
			}
			else if (state.equals("OFFLINE"))
			{
			}
		}
		else if (action.equals(de.tubs.ibr.dtn.Intent.RECEIVE))
		{
			// open activity
			Intent i = new Intent(context, DTalkieService.class);
			i.setAction(de.tubs.ibr.dtn.Intent.RECEIVE);
			context.startService(i);
		}
	}
}
