package fr.epistudio.epysia.physics.api;

public interface ContactImpulseSnapshot extends AutoCloseable {
    ContactImpulseSnapshot EMPTY = new ContactImpulseSnapshot() {
        @Override
        public int restore() {
            return 0;
        }

        @Override
        public int contactCount() {
            return 0;
        }

        @Override
        public void close() {
        }
    };

    int restore();

    int contactCount();

    @Override
    void close();
}
