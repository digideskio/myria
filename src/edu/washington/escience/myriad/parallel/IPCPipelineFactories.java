package edu.washington.escience.myriad.parallel;

import java.util.concurrent.LinkedBlockingQueue;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.compression.ZlibDecoder;
import org.jboss.netty.handler.codec.compression.ZlibEncoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufDecoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufEncoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;

import edu.washington.escience.myriad.parallel.Worker.MessageWrapper;
import edu.washington.escience.myriad.proto.TransportProto;

public class IPCPipelineFactories {
  public static class WorkerServerPipelineFactory implements ChannelPipelineFactory {

    /**
     * constructor.
     * */
    WorkerServerPipelineFactory(final LinkedBlockingQueue<MessageWrapper> messageBuffer) {
      this.messageBuffer = messageBuffer;
    }

    LinkedBlockingQueue<MessageWrapper> messageBuffer;

    @Override
    public ChannelPipeline getPipeline() throws Exception {
      ChannelPipeline p = Channels.pipeline();
      p.addLast("compressionDecoder", new ZlibDecoder()); // upstream 1
      p.addLast("frameDecoder", new ProtobufVarint32FrameDecoder()); // upstream 2
      p.addLast("protobufDecoder", new ProtobufDecoder(TransportProto.TransportMessage.getDefaultInstance())); // upstream
                                                                                                               // 3

      p.addLast("compressionEncoder", new ZlibEncoder()); // downstream 1
      p.addLast("frameEncoder", new ProtobufVarint32LengthFieldPrepender()); // downstream 2
      p.addLast("protobufEncoder", new ProtobufEncoder()); // downstream 3

      p.addLast("inputVerifier", new IPCInputGuard());
      p.addLast("controlHandler", new WorkerControlHandler());
      p.addLast("dataHandler", new WorkerDataHandler(messageBuffer));
      return p;
    }
  }

  public static class WorkerClientPipelineFactory implements ChannelPipelineFactory {
    /**
     * constructor.
     * */
    WorkerClientPipelineFactory(final LinkedBlockingQueue<MessageWrapper> messageBuffer) {
      this.messageBuffer = messageBuffer;
    }

    LinkedBlockingQueue<MessageWrapper> messageBuffer;

    @Override
    public ChannelPipeline getPipeline() throws Exception {
      return new WorkerServerPipelineFactory(messageBuffer).getPipeline();
    }
  }

  public static class MasterServerPipelineFactory implements ChannelPipelineFactory {

    /**
     * constructor.
     * */
    MasterServerPipelineFactory(final LinkedBlockingQueue<MessageWrapper> messageBuffer) {
      this.messageBuffer = messageBuffer;
    }

    LinkedBlockingQueue<MessageWrapper> messageBuffer;

    @Override
    public ChannelPipeline getPipeline() throws Exception {
      ChannelPipeline p = Channels.pipeline();
      p.addLast("compressionDecoder", new ZlibDecoder()); // upstream 1
      p.addLast("frameDecoder", new ProtobufVarint32FrameDecoder()); // upstream 2
      p.addLast("protobufDecoder", new ProtobufDecoder(TransportProto.TransportMessage.getDefaultInstance())); // upstream
                                                                                                               // 3

      p.addLast("compressionEncoder", new ZlibEncoder()); // downstream 1
      p.addLast("frameEncoder", new ProtobufVarint32LengthFieldPrepender()); // downstream 2
      p.addLast("protobufEncoder", new ProtobufEncoder()); // downstream 3

      p.addLast("inputVerifier", new IPCInputGuard());
      p.addLast("controlHandler", new MasterControlHandler());
      p.addLast("dataHandler", new MasterDataHandler(messageBuffer));
      return p;
    }
  }

  public static class MasterClientPipelineFactory implements ChannelPipelineFactory {

    /**
     * constructor.
     * */
    MasterClientPipelineFactory(final LinkedBlockingQueue<MessageWrapper> messageBuffer) {
      this.messageBuffer = messageBuffer;
    }

    LinkedBlockingQueue<MessageWrapper> messageBuffer;

    @Override
    public ChannelPipeline getPipeline() throws Exception {
      return new MasterServerPipelineFactory(messageBuffer).getPipeline();
    }
  }
}
