package net.osmand.plus.server.endpoints;

import android.graphics.Bitmap;
import android.util.Pair;
import fi.iki.elonen.NanoHTTPD;
import net.osmand.PlatformUtil;
import net.osmand.data.RotatedTileBox;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.render.MapRenderRepositories;
import net.osmand.plus.resources.AsyncLoadingThread;
import net.osmand.plus.server.ApiEndpoint;
import net.osmand.plus.server.ApiRouter;
import org.apache.commons.logging.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.*;

import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;

public class TileEndpoint implements ApiEndpoint {
	private static final Log LOG = PlatformUtil.getLog(TileEndpoint.class);
	ExecutorService executor = Executors.newFixedThreadPool(3);
	Map<RotatedTileBox, Bitmap> hashMap = new HashMap<>();
	Map<RotatedTileBox, Bitmap> map = Collections.synchronizedMap(hashMap);
	OsmandApplication application;

	public TileEndpoint(OsmandApplication application) {
		this.application = application;
	}

	@Override
	public NanoHTTPD.Response process(NanoHTTPD.IHTTPSession session) {
		try {
			return tileApiCall(session);
		} catch (Exception e) {
			LOG.error("Exception", e);
		}
		return ApiRouter.ErrorResponses.response500;
	}

	@Override
	public void setApplication(OsmandApplication application) {
		this.application = application;
	}

	private synchronized NanoHTTPD.Response tileApiCall(NanoHTTPD.IHTTPSession session) {
		int zoom;
		double lat;
		double lon;
		String fullUri = session.getUri().replace("/tile/", "");
		Scanner s = new Scanner(fullUri).useDelimiter("/");
		zoom = s.nextInt();
		lat = s.nextDouble();
		lon = s.nextDouble();
		Future<Pair<RotatedTileBox, Bitmap>> future;
		final RotatedTileBox rotatedTileBox = new RotatedTileBox.RotatedTileBoxBuilder()
				.setLocation(lat, lon)
				.setZoom(zoom)
				.setPixelDimensions(512, 512, 0.5f, 0.5f).build();
		final MapRenderRepositories renderer = application.getResourceManager().getRenderer();
		future = executor.submit(new Callable<Pair<RotatedTileBox, Bitmap>>() {
			@Override
			public Pair<RotatedTileBox, Bitmap> call() throws Exception {
				Bitmap bmp;
				while ((bmp = map.get(rotatedTileBox)) == null) {
					Thread.sleep(500);
				}
				return Pair.create(rotatedTileBox, bmp);
			}
		});
		application.getResourceManager().updateRendererMap(rotatedTileBox, new AsyncLoadingThread.OnMapLoadedListener() {
			@Override
			public void onMapLoaded(boolean interrupted) {
				map.put(rotatedTileBox, renderer.getBitmap());
			}
		});
		try {
			Pair<RotatedTileBox, Bitmap> pair = future.get();
			Bitmap bitmap = pair.second;
			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
			byte[] byteArray = stream.toByteArray();
			ByteArrayInputStream str = new ByteArrayInputStream(byteArray);
			return newFixedLengthResponse(
					NanoHTTPD.Response.Status.OK,
					"image/png",
					str,
					str.available());
		} catch (ExecutionException e) {
			LOG.error("Execution exception", e);
			return ApiRouter.ErrorResponses.response500;
		} catch (InterruptedException e) {
			LOG.error("Interrupted exception", e);
			return ApiRouter.ErrorResponses.response500;
		}
	}
}
