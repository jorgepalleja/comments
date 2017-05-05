//========================================================================
//Copyright 2017 David Yu
//------------------------------------------------------------------------
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at 
//http://www.apache.org/licenses/LICENSE-2.0
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.
//========================================================================

package comments.user;

import java.io.IOException;

import com.dyuproject.protostuff.Input;
import com.dyuproject.protostuff.JsonXOutput;
import com.dyuproject.protostuff.Output;
import com.dyuproject.protostuff.Pipe;
import com.dyuproject.protostuff.RpcHeader;
import com.dyuproject.protostuff.RpcResponse;
import com.dyuproject.protostuff.RpcRuntimeExceptions;
import com.dyuproject.protostuff.ds.P8;
import com.dyuproject.protostuff.ds.ParamRangeKey;
import com.dyuproject.protostuffdb.Datastore;
import com.dyuproject.protostuffdb.ProtostuffPipe;
import com.dyuproject.protostuffdb.RangeV;
import com.dyuproject.protostuffdb.Visit;
import com.dyuproject.protostuffdb.Visitor;
import com.dyuproject.protostuffdb.WriteContext;

/**
 * TODO
 * 
 * @author David Yu
 * @created May 1, 2017
 */
public final class CommentViews
{
    private CommentViews() {}
    
    private static final ParamRangeKey ASC = new ParamRangeKey(false);
    static
    {
        ASC.limit = -1;
    }
    
    static final Pipe.Schema<Comment> PS = new Pipe.Schema<Comment>(Comment.getSchema())
    {
        @Override
        protected void transfer(Pipe pipe, Input input, Output output) throws IOException
        {
            for (int number = input.readFieldNumber(wrappedSchema);
                    number != 0;
                    number = input.readFieldNumber(wrappedSchema))
            {
                // exclude keychain
                if (number == Comment.FN_KEY_CHAIN)
                    input.handleUnknownField(number, wrappedSchema);
                else
                    Comment.transferField(number, pipe, input, output, wrappedSchema);
            }
        }
    };
    
    static Pipe.Schema<Comment> psComment(RpcHeader header)
    {
        return PS;
    }
    
    static final int MAX_SIZE = 1 + 8 // ts
            + 1 + 2 + 9 + (127 * 9) // key_chain
            + 1 + 1 + 127 // name
            + 1 + 2 + 1024 // content
            + 1 + 8 // post_id
            + 1 + 1 // depth
            + 1 + 9; // parent_key
    
    static final int MAX_LIMIT = 0xFFFF - MAX_SIZE - 2; // 2 is the delimiter size
    
    static final Visitor<RpcResponse> PV = new Visitor<RpcResponse>()
    {
        public boolean visit(byte[] key, 
                byte[] v, int voffset, int vlen, 
                RpcResponse res, int index)
        {
            final ProtostuffPipe pipe = res.context.pipe.set(key, v, voffset, vlen);
            try
            {
                res.writeRawNested(pipe.fieldNumber(), 
                        pipe, pipe.schema(), pipe.repeated());
            }
            catch (IOException e)
            {
                throw RpcRuntimeExceptions.pipe(e);
            }
            
            return ((JsonXOutput)res.output).getSize() >= MAX_LIMIT;
        }
    };
    
    static <V> boolean visitByPostId(long postId, 
            Datastore store, WriteContext context, 
            Visitor<V> visitor, V param)
    {
        return visitByPostId(postId, store, context, RangeV.Store.V, visitor, param);
    }
    
    static <V> boolean visitByPostId(long postId, 
            Datastore store, WriteContext context, 
            RangeV.Store visitorType, 
            Visitor<V> visitor, V param)
    {
        return Visit.by8(Comment.IDX_POST_ID__KEY_CHAIN, postId,
                Comment.EM, Comment.PList.FN_P, ASC,
                visitorType, store, context,
                visitor, param);
    }

    static boolean listAllByPostId(ParamLong req, Datastore store, RpcResponse res,
            Pipe.Schema<Comment.PList> resPipeSchema, RpcHeader header)
    {
        res.context.ps = PS;
        
        return visitByPostId(req.p,
                store, res.context,
                RangeV.Store.CONTEXT_PV,
                PV, res);
    }

    static boolean listByPostId(P8 req, Datastore store, RpcResponse res,
            Pipe.Schema<Comment.PList> resPipeSchema, RpcHeader header)
    {
        res.context.ps = PS;
        
        return Visit.by8(Comment.IDX_POST_ID__KEY_CHAIN, req,
                Comment.EM, Comment.PList.FN_P,
                RangeV.Store.CONTEXT_PV, store, res.context,
                PV, res);
    }
}
