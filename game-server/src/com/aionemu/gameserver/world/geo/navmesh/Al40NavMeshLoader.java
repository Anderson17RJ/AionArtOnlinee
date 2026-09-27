package com.aionemu.gameserver.world.geo.navmesh;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Streaming reader for ALGeoBuilder's little-endian AL40 format. */
final class Al40NavMeshLoader {

	private static final int AL40_MAGIC = 0x30344C41;
	private static final int NAVM_MAGIC = 0x4D76614E;
	private static final int MAX_SUBGRAPHS = 50_000;
	private static final int MAX_GRID_CELLS = 100_000_000;

	private Al40NavMeshLoader() {
	}

	static Al40NavMesh load(int worldId, Path file) throws IOException {
		try (LittleEndianInput input = new LittleEndianInput(new BufferedInputStream(Files.newInputStream(file), 1 << 20))) {
			if (input.readInt() != AL40_MAGIC)
				throw new IOException(file + " is not an AL40 navigation mesh");
			int subgraphCount = input.readInt();
			if (subgraphCount < 1 || subgraphCount > MAX_SUBGRAPHS)
				throw new IOException("Invalid AL40 subgraph count: " + subgraphCount);
			List<Al40NavMesh.Subgraph> subgraphs = new ArrayList<>(subgraphCount);
			long totalFloors = 0;
			for (int index = 0; index < subgraphCount; index++) {
				if (input.readInt() != NAVM_MAGIC)
					throw new IOException("Invalid NavM magic at subgraph " + index);
				int width = input.readUnsignedShort();
				int height = input.readUnsignedShort();
				float step = input.readFloat();
				float maxZStep = input.readFloat();
				float minX = input.readFloat();
				float minY = input.readFloat();
				int minZ = input.readInt();
				int maxZ = input.readInt();
				long cellCount = (long) width * height;
				if (width < 1 || height < 1 || cellCount > MAX_GRID_CELLS || !Float.isFinite(step) || step < 0.01f
					|| !Float.isFinite(maxZStep) || maxZStep < 0 || maxZ < minZ)
					throw new IOException("Invalid AL40 subgraph header at index " + index);
				int[] grid = new int[(int) cellCount];
				for (int cell = 0; cell < grid.length; cell++) {
					grid[cell] = input.readInt();
					totalFloors += grid[cell] >>> 24;
				}
				int multiHeightLength = input.readInt();
				if (multiHeightLength < 0 || multiHeightLength > grid.length * 255L * 3)
					throw new IOException("Invalid multiheight length at subgraph " + index + ": " + multiHeightLength);
				byte[] multiHeights = input.readBytes(multiHeightLength);
				subgraphs.add(new Al40NavMesh.Subgraph(width, height, step, maxZStep, minX, minY, minZ, maxZ, grid, multiHeights));
			}
			if (input.read() != -1)
				throw new IOException("Trailing bytes after the last AL40 subgraph");
			return new Al40NavMesh(worldId, subgraphs, totalFloors);
		}
	}

	private static final class LittleEndianInput implements AutoCloseable {
		private final InputStream input;

		private LittleEndianInput(InputStream input) {
			this.input = input;
		}

		int read() throws IOException {
			return input.read();
		}

		int readUnsignedShort() throws IOException {
			int b0 = requiredByte();
			int b1 = requiredByte();
			return b0 | b1 << 8;
		}

		int readInt() throws IOException {
			int b0 = requiredByte();
			int b1 = requiredByte();
			int b2 = requiredByte();
			int b3 = requiredByte();
			return b0 | b1 << 8 | b2 << 16 | b3 << 24;
		}

		float readFloat() throws IOException {
			return Float.intBitsToFloat(readInt());
		}

		byte[] readBytes(int length) throws IOException {
			byte[] bytes = new byte[length];
			int offset = 0;
			while (offset < length) {
				int count = input.read(bytes, offset, length - offset);
				if (count < 0)
					throw new EOFException();
				offset += count;
			}
			return bytes;
		}

		private int requiredByte() throws IOException {
			int value = input.read();
			if (value < 0)
				throw new EOFException();
			return value;
		}

		@Override
		public void close() throws IOException {
			input.close();
		}
	}
}
